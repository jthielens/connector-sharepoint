package com.cleo.labs.connector.sharepoint;

import static com.cleo.connector.api.command.ConnectorCommandName.ATTR;
import static com.cleo.connector.api.command.ConnectorCommandName.DELETE;
import static com.cleo.connector.api.command.ConnectorCommandName.DIR;
import static com.cleo.connector.api.command.ConnectorCommandName.GET;
import static com.cleo.connector.api.command.ConnectorCommandName.MKDIR;
import static com.cleo.connector.api.command.ConnectorCommandName.PUT;
import static com.cleo.connector.api.command.ConnectorCommandName.RMDIR;
import static com.cleo.connector.api.command.ConnectorCommandOption.Delete;
import static com.cleo.connector.api.command.ConnectorCommandOption.Unique;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.annotations.Command;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
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
import com.independentsoft.share.Service;
import com.independentsoft.share.ServiceException;
import com.independentsoft.share.queryoptions.Filter;
import com.independentsoft.share.queryoptions.IQueryOption;
import com.independentsoft.share.queryoptions.IsEqualTo;

public class SharePointConnectorClient extends ConnectorClient {
    private SharePointConnectorConfig config;
    private Service service;
    private String prefix;

    /**
     * Constructs a new {@code SharePointConnectorClient} for the schema using
     * the default config wrapper
     * 
     * @param schema the {@code BlobStorageConnectorSchema}
     */
    public SharePointConnectorClient(SharePointConnectorSchema schema) {
        this.config = new SharePointConnectorConfig(this, schema);
        this.service = null;
        this.prefix = null;
    }

    /**
     * Establishes a live container reference from the configuration.
     * 
     * @throws InvalidKeyException
     * @throws ConnectorPropertyException
     * @throws URISyntaxException
     * @throws StorageException
     */
    private synchronized void setup() throws ConnectorPropertyException {
        if (service == null) {
            logger.debug("connecting to "+config.getServiceURL()+" as "+config.getUsername());
            service = new Service(config.getServiceURL(), config.getUsername(), config.getPassword());
            prefix = service.getSiteUrl().replaceFirst("[^/]*//[^/]*", "");
        }
    }

    private static Path normalize(String path) {
        return Paths.get(path.replaceFirst("^(?=[^/]|$)","/"));
    }

    @Command(name = DIR)
    public ConnectorCommandResult dir(DirCommand dir) throws ConnectorPropertyException, ServiceException {
        String path = dir.getSource().getPath();

        logger.debug(String.format("DIR '%s'", path));
        setup();

        if (path.equals(".")) path = ""; // TODO: remove when Harmony is fixed

        List<Entry> list = new ArrayList<>();
        for (Folder f : service.getFolders(path)) {
            Entry entry = new Entry(Type.dir)
                    .setPath(f.getName())
                    .setDate(Attributes.toLocalDateTime(f.getLastModifiedTime()))
                    .setSize(-1L);
            // --> System.err.println(f.getServerRelativeUrl());
            list.add(entry);
        }
        for (File f : service.getFiles(path)) {
            Entry entry = new Entry(Type.file)
                    .setPath(f.getName())
                    .setDate(Attributes.toLocalDateTime(f.getLastModifiedTime()))
                    .setSize(f.getLength());
            // --> System.err.println(f.getServerRelativeUrl());
            list.add(entry);
        }
        return new ConnectorCommandResult(Status.Success, Optional.empty(), list);
    }

    @Command(name = GET, options = { Delete })
    public ConnectorCommandResult get(GetCommand get) throws ConnectorException, IOException {
        String path = get.getSource().getPath();
        IConnectorIncoming destination = get.getDestination();

        logger.debug(String.format("GET remote '%s' to local '%s'", path, destination.getPath()));
        setup();
        Path normalized = normalize(path);

        try (InputStream is = service.getFileStream(prefix+normalized.toString())) {
            transfer(is, destination.getStream(), true);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
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
        String path = put.getDestination().getPath();
        IConnectorOutgoing source = put.getSource();
        String filename = bestFilename(put);

        logger.debug(String.format("PUT local '%s' to remote '%s' (matching filename '%s')", source.getPath(), path,
                filename));
        setup();
        Path normalized = normalize(path);

        // boolean unique = ConnectorCommandUtil.isOptionOn(put.getOptions(), Unique);

        try {
            // TODO: implement Unique
            service.createFile(prefix+normalized.toString(), put.getSource().getStream());
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            System.err.println("Error Message: " + e.getMessage());
            System.err.println("Error Code   : " + e.getErrorCode());
            System.err.println("Error String : " + e.getErrorString());
            System.err.println("Error Request: " + e.getRequestUrl());
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    private Optional<File> getFile(Path normalized) {
        if (normalized.getNameCount()==0) {
            // root is a folder
            return Optional.empty();
        }

        // go search for it
        try {
            List<IQueryOption> named = Collections.singletonList(new Filter(new IsEqualTo("name",normalized.getFileName().toString())));
            List<File> files = service.getFiles(prefix+normalized.getParent().toString(), named);
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

    private Optional<Folder> getFolder(Path normalized) {
        if (normalized.getNameCount()==0) {
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
            List<IQueryOption> named = Collections.singletonList(new Filter(new IsEqualTo("name",normalized.getFileName().toString())));
            List<Folder> folders = service.getFolders(prefix+normalized.getParent().toString(), named);
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
     * @param path the file path
     * @return the file attributes
     * @throws ServiceException 
     * @throws com.cleo.connector.api.ConnectorException
     * @throws java.io.IOException
     * @throws StorageException 
     * @throws URISyntaxException 
     * @throws InvalidKeyException 
     */
    @Command(name = ATTR)
    public BasicFileAttributeView getAttributes(String path) throws ConnectorException, ServiceException {
        logger.debug(String.format("ATTR '%s'", path));
        setup();
        if (path.equals(".")) path = ""; // TODO: remove when Harmony is fixed
        Path normalized = normalize(path);

        Optional<File> file = getFile(normalized);
        if (file.isPresent()) {
            return new SharePointFileAttributes(file.get(), logger);
        }
        Optional<Folder> folder = getFolder(normalized);
        if (folder.isPresent()) {
            return new SharePointFolderAttributes(folder.get(), logger);
        }
        throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
                ConnectorException.Category.fileNonExistentOrNoAccess);
    }

    @Command(name = DELETE)
    public ConnectorCommandResult delete(OtherCommand delete) throws ConnectorException {
        String path = delete.getSource();
        logger.debug(String.format("DELETE '%s'", path));
        setup();
        Path normalized = normalize(path);

        try {
            service.deleteFile(prefix+normalized.toString());
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

    @Command(name = MKDIR)
    public ConnectorCommandResult mkdir(OtherCommand mkdir) throws ConnectorException {
        String path = mkdir.getSource();
        logger.debug(String.format("MKDIR '%s'", path));
        setup();
        if (path.equals(".")) path = ""; // TODO: remove when Harmony is fixed
        Path normalized = normalize(path);

        try {
            service.createFolder(prefix+normalized.toString());
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException("MKDIR cannot create folder "+path, e);
        }
    }

    @Command(name = RMDIR)
    public ConnectorCommandResult rmdir(OtherCommand mkdir) throws ConnectorException {
        String path = mkdir.getSource();
        logger.debug(String.format("RMDIR '%s'", path));
        setup();
        if (path.equals(".")) path = ""; // TODO: remove when Harmony is fixed
        Path normalized = normalize(path);

        try {
            service.deleteFolder(prefix+normalized.toString());
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (ServiceException e) {
            throw new ConnectorException("RMDIR cannot delete folder "+path, e);
        }
    }
}
