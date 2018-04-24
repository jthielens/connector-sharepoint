package com.cleo.labs.connector.sharepoint;

import static com.cleo.connector.api.command.ConnectorCommandName.ATTR;
import static com.cleo.connector.api.command.ConnectorCommandName.DELETE;
import static com.cleo.connector.api.command.ConnectorCommandName.DIR;
import static com.cleo.connector.api.command.ConnectorCommandName.GET;
import static com.cleo.connector.api.command.ConnectorCommandName.MKDIR;
import static com.cleo.connector.api.command.ConnectorCommandName.PUT;
import static com.cleo.connector.api.command.ConnectorCommandName.RENAME;
import static com.cleo.connector.api.command.ConnectorCommandName.RMDIR;
import static com.cleo.connector.api.command.ConnectorCommandOption.Delete;
import static com.cleo.connector.api.command.ConnectorCommandOption.Unique;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.apache.commons.io.FilenameUtils;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.annotations.Command;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.command.ConnectorCommandUtil;
import com.cleo.connector.api.command.DirCommand;
import com.cleo.connector.api.command.GetCommand;
import com.cleo.connector.api.command.OtherCommand;
import com.cleo.connector.api.command.PutCommand;
import com.cleo.connector.api.directory.Directory.Type;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.helper.Attributes;
import com.cleo.connector.api.interfaces.IConnectorIncoming;
import com.cleo.connector.api.interfaces.IConnectorOutgoing;
import com.cleo.connector.api.property.ConnectorPropertyException;
import com.google.common.base.Strings;
import com.independentsoft.share.File;
import com.independentsoft.share.Folder;
import com.independentsoft.share.MoveOperation;
import com.independentsoft.share.Service;
import com.independentsoft.share.ServiceException;
import com.independentsoft.share.queryoptions.Filter;
import com.independentsoft.share.queryoptions.IQueryOption;
import com.independentsoft.share.queryoptions.IsEqualTo;

public class SharePointConnectorClient extends ConnectorClient {
    private SharePointConnectorConfig config;
    private Service service;
    private String prefix;
    private String clientkey;

    /**
     * Constructs a new {@code SharePointConnectorClient} for the schema
     * 
     * @param schema the {@code SharePointConnectorSchema}
     */
    public SharePointConnectorClient(SharePointConnectorSchema schema) {
        this.config = new SharePointConnectorConfig(this, schema);
        this.service = null;
        this.prefix = null;
    }

    /**
     * Establishes a {@link Service} reference for the client
     * 
     * @throws ConnectorPropertyException
     */
    private synchronized void setup() throws ConnectorPropertyException {
        if (service == null) {
            logger.debug("connecting to "+config.getServiceURL()+" as "+config.getUsername());
            if (Strings.isNullOrEmpty(config.getDomain())) {
                service = new Service(config.getServiceURL(), config.getUsername(), config.getPassword());
                clientkey = config.getUsername()+"@"+config.getServiceURL();
            } else {
                service = new Service(config.getServiceURL(), config.getUsername(), config.getPassword(), config.getDomain());
                clientkey = config.getUsername()+"@"+config.getDomain()+"@"+config.getServiceURL();
            }
            prefix = service.getSiteUrl().replaceFirst("[^/]*//[^/]*", "");
        }
    }

    @Command(name = DIR)
    public ConnectorCommandResult dir(DirCommand dir) throws ConnectorPropertyException, ServiceException {
        String source = dir.getSource().getPath();

        logger.debug(String.format("DIR '%s'", source));
        setup();

        if (source.equals(".")) source = ""; // TODO: remove when Harmony is fixed
        Path sourcePath = new Path(source);

        List<Entry> list = new ArrayList<>();
        for (Folder f : service.getFolders(source)) {
            Entry entry = new Entry(Type.dir)
                    .setPath(sourcePath.child(f.getName()).toString())
                    .setDate(Attributes.toLocalDateTime(f.getLastModifiedTime()))
                    .setSize(-1L);
            list.add(entry);
            AttrCache.put(clientkey, sourcePath.child(f.getName()), new SharePointFolderAttributes(f, logger));
        }
        for (File f : service.getFiles(source)) {
            Entry entry = new Entry(Type.file)
                    .setPath(sourcePath.child(f.getName()).toString())
                    .setDate(Attributes.toLocalDateTime(f.getLastModifiedTime()))
                    .setSize(f.getLength());
            list.add(entry);
            AttrCache.put(clientkey, sourcePath.child(f.getName()), new SharePointFileAttributes(f, logger));
        }
        return new ConnectorCommandResult(Status.Success, Optional.empty(), list);
    }

    @Command(name = GET, options = { Delete })
    public ConnectorCommandResult get(GetCommand get) throws ConnectorException, IOException {
        String source = get.getSource().getPath();
        IConnectorIncoming destination = get.getDestination();

        logger.debug(String.format("GET remote '%s' to local '%s'", source, destination.getPath()));
        setup();
        Path sourcePath = new Path(source);

        try (InputStream is = service.getFileStream(prefix+sourcePath.toString())) {
            transfer(is, destination.getStream(), true);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    /**
     * Figures out the best intent of the user for the destination filename to
     * use:
     * <ul>
     * <li>if a destination path is provided, use it (e.g. PUT source
     * destination or through a URI, LCOPY source router:host/destination).</li>
     * <li>if the destination path matches the host alias (e.g. LCOPY source
     * router:host), prefer the source filename</li>
     * <li>if the destination is not useful and the source is not empty, use
     * it</li>
     * 
     * @param put the {@link PutCommand}
     * @return a String to use as the filename
     */
    private String bestFilename(PutCommand put) {
        String destination = put.getDestination().getPath();
        if (Strings.isNullOrEmpty(destination) || destination.equals(getHost().getAlias())) {
            String source = put.getSource().getPath();
            if (!Strings.isNullOrEmpty(source)) {
                destination = source;
            }
        }
        return destination;
    }

    @Command(name = PUT, options = { Unique, Delete })
    public ConnectorCommandResult put(PutCommand put) throws ConnectorException {
        String destination = put.getDestination().getPath();
        IConnectorOutgoing source = put.getSource();
        String filename = bestFilename(put);

        logger.debug(String.format("PUT local '%s' to remote '%s' (matching filename '%s')", source.getPath(), destination,
                filename));
        setup();
        Path destinationPath = new Path(destination);

        boolean unique = ConnectorCommandUtil.isOptionOn(put.getOptions(), Unique);

        try {
            Optional<File> test = getFile(destinationPath);
            if (unique && test.isPresent()) {
                Path parent = destinationPath.parent();
                String fn = destinationPath.name();
                int counter = 0;
                String ext = FilenameUtils.getExtension(fn).replaceFirst("^(?=[^\\.])","."); // prefix with "." unless empty or already "."
                String base = fn.substring(0, fn.length()-ext.length());
                Path candidate;

                do {
                    counter++;
                    candidate = parent.child(base+"."+counter+ext);
                    test = getFile(candidate);
                } while (test.isPresent());
                destinationPath = candidate;
            }
            if (test.isPresent()) {
                service.updateFileContent(prefix+destinationPath.toString(), put.getSource().getStream());
            } else {
                service.createFile(prefix+destinationPath.toString(), put.getSource().getStream());
            }
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            System.err.println("Error Message: " + e.getMessage());
            System.err.println("Error Code   : " + e.getErrorCode());
            System.err.println("Error String : " + e.getErrorString());
            System.err.println("Error Request: " + e.getRequestUrl());
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", destination),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    private Optional<File> getFile(Path path) {
        if (path.size()==0) {
            // root is a folder
            return Optional.empty();
        }

        // go search for it
        try {
            List<IQueryOption> named = Collections.singletonList(new Filter(new IsEqualTo("name",path.name().toString())));
            List<File> files = service.getFiles(prefix+path.parent().toString(), named);
            if (files.size() == 1) {
                return Optional.of(files.get(0));
            } else if (files.isEmpty()) {
                System.err.println(named.get(0).toString()+" did not find anything");
            } else {
                System.err.println(named.get(0).toString()+" matched "+files.size()+" files");
            }
        } catch (ServiceException e) {
            // this will 404 in case of not found or a folder
            System.err.println("Error Message: " + e.getMessage());
            System.err.println("Error Code   : " + e.getErrorCode());
            System.err.println("Error String : " + e.getErrorString());
            System.err.println("Error Request: " + e.getRequestUrl());
        }
        return Optional.empty();
    }

    private Optional<Folder> getFolder(Path path) {
        if (path.size()==0) {
            // root folder
            try {
                return Optional.of(service.getFolder(prefix));
            } catch (ServiceException e) {
                System.err.println("Error Message: " + e.getMessage());
                System.err.println("Error Code   : " + e.getErrorCode());
                System.err.println("Error String : " + e.getErrorString());
                System.err.println("Error Request: " + e.getRequestUrl());
            }
            return Optional.empty();
        }

        // go search for it
        try {
            List<IQueryOption> named = Collections.singletonList(new Filter(new IsEqualTo("name",path.name().toString())));
            List<Folder> folders = service.getFolders(prefix+path.parent().toString(), named);
            if (folders.size() == 1) {
                return Optional.of(folders.get(0));
            } else if (folders.isEmpty()) {
                System.err.println(named.get(0).toString()+" did not find anything");
            } else {
                System.err.println(named.get(0).toString()+" matched "+folders.size()+" files");
            }
        } catch (ServiceException e) {
            // this will 404 in case of not found or a folder
            System.err.println("Error Message: " + e.getMessage());
            System.err.println("Error Code   : " + e.getErrorCode());
            System.err.println("Error String : " + e.getErrorString());
            System.err.println("Error Request: " + e.getRequestUrl());
        }
        return Optional.empty();
    }

    /**
     * Get the file attribute view associated with a file path
     * 
     * @param source the file path
     * @return the file attributes
     * @throws ServiceException 
     * @throws com.cleo.connector.api.ConnectorException
     * @throws java.io.IOException
     * @throws StorageException 
     * @throws URISyntaxException 
     * @throws InvalidKeyException 
     */
    @Command(name = ATTR)
    public BasicFileAttributeView getAttributes(String source) throws ConnectorException, ServiceException {
        logger.debug(String.format("ATTR '%s'", source));
        setup();
        if (source.equals(".")) source = ""; // TODO: remove when Harmony is fixed
        Path sourcePath = new Path(source);

        Optional<BasicFileAttributeView> attr = Optional.empty();
        try {
            attr = AttrCache.get(clientkey, sourcePath, new Callable<Optional<BasicFileAttributeView>>() {
                @Override
                public Optional<BasicFileAttributeView> call() {
                    logger.debug(String.format("fetching attributes for '%s'", sourcePath.toString()));
                    Optional<File> file = getFile(sourcePath);
                    if (file.isPresent()) {
                        return Optional.of(new SharePointFileAttributes(file.get(), logger));
                    }
                    Optional<Folder> folder = getFolder(sourcePath);
                    if (folder.isPresent()) {
                        return Optional.of(new SharePointFolderAttributes(folder.get(), logger));
                    }
                    return Optional.empty();
                }
            });
        } catch (Exception e) {
            throw new ConnectorException(String.format("error getting attributes for '%s'", source), e);
        }
        if (attr.isPresent()) {
            return attr.get();
        } else {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    @Command(name = DELETE)
    public ConnectorCommandResult delete(OtherCommand delete) throws ConnectorException {
        String source = delete.getSource();
        logger.debug(String.format("DELETE '%s'", source));
        setup();
        Path sourcePath = new Path(source);

        try {
            service.deleteFile(prefix+sourcePath.toString());
            AttrCache.invalidate(clientkey, sourcePath);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    @Command(name = MKDIR)
    public ConnectorCommandResult mkdir(OtherCommand mkdir) throws ConnectorException {
        String source = mkdir.getSource();
        logger.debug(String.format("MKDIR '%s'", source));
        setup();
        if (source.equals(".")) source = ""; // TODO: remove when Harmony is fixed
        Path sourcePath = new Path(source);

        try {
            service.createFolder(prefix+sourcePath.toString());
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException("MKDIR cannot create folder "+source, e);
        }
    }

    @Command(name = RMDIR)
    public ConnectorCommandResult rmdir(OtherCommand mkdir) throws ConnectorException {
        String source = mkdir.getSource();
        logger.debug(String.format("RMDIR '%s'", source));
        setup();
        if (source.equals(".")) source = ""; // TODO: remove when Harmony is fixed
        Path sourcePath = new Path(source);

        try {
            service.deleteFolder(prefix+sourcePath.toString());
            AttrCache.invalidate(clientkey, sourcePath);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException("RMDIR cannot delete folder "+source, e);
        }
    }

    @Command(name = RENAME)
    public ConnectorCommandResult rename(OtherCommand rename) throws ConnectorException {
        String source = rename.getSource();
        String destination = rename.getDestination();
        logger.debug(String.format("RENAME '%s' '%s'", source, destination));
        setup();
        Path sourcePath = new Path(source);
        Path destinationPath = new Path(destination);

        Optional<File> sourceFile = getFile(sourcePath);
        try {
            if (sourceFile.isPresent()) {
                if (service.moveFile(prefix+sourcePath.toString(), prefix+destinationPath.toString(), MoveOperation.OVERWRITE)) {
                    return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
                } else {
                    return new ConnectorCommandResult(Status.Error, String.format("RENAME '%s' '%s' failed.", source, destination));
                }
            } else {
                throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                        ConnectorException.Category.fileNonExistentOrNoAccess);
            }
        } catch (ServiceException e) {
            throw new ConnectorException(String.format("RENAME cannot rename '%s' to '%s'", source, destination), e);
        }
    }
}
