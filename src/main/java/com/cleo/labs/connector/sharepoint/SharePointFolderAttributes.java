package com.cleo.labs.connector.sharepoint;

import java.io.IOException;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;

import com.cleo.connector.api.helper.Logger;
import com.independentsoft.share.Folder;

/**
 * SharePoint file attribute views
 */
public class SharePointFolderAttributes implements DosFileAttributes, DosFileAttributeView {
    private Folder folder;
    private Logger logger;

    public SharePointFolderAttributes(Folder file, Logger logger) {
        this.folder = file;
        this.logger = logger;
    }

    @Override
    public FileTime lastModifiedTime() {
        logger.debug(String.format("lastModifiderTime()=%s", folder.getLastModifiedTime().toString()));
        return FileTime.fromMillis(folder.getLastModifiedTime().getTime());
    }

    @Override
    public FileTime lastAccessTime() {
        logger.debug(String.format("lastAccessTime()=%s", folder.getLastModifiedTime().toString()));
        return FileTime.fromMillis(folder.getLastModifiedTime().getTime());
    }

    @Override
    public FileTime creationTime() {
        logger.debug(String.format("creationTime()=%s", folder.getLastModifiedTime().toString()));
        return FileTime.fromMillis(folder.getLastModifiedTime().getTime());
    }

    @Override
    public boolean isRegularFile() {
        logger.debug("isRegularFile()=false");
        return false; // folders are directories
    }

    @Override
    public boolean isDirectory() {
        logger.debug("isDirectory()=true");
        return true; // folders are directories
    }

    @Override
    public boolean isSymbolicLink() {
        logger.debug("isSymbolicLink()=false");
        return false; // folders are directories
    }

    @Override
    public boolean isOther() {
        logger.debug("isOther()=false");
        return false; // folders are directories
    }

    @Override
    public long size() {
        logger.debug("size()=-1L");
        return -1L;
    }

    @Override
    public Object fileKey() {
        logger.debug("fileKey()=null");
        return null;
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        if (lastModifiedTime != null || lastAccessTime != null || createTime != null) {
            throw new UnsupportedOperationException("setTimes() not supported for SharePoint");
        }
    }

    @Override
    public String name() {
        return "blob";
    }

    @Override
    public DosFileAttributes readAttributes() throws IOException {
        return this;
    }

    @Override
    public void setReadOnly(boolean value) throws IOException {
        throw new UnsupportedOperationException("setHidden() not supported for SharePoint");
    }

    @Override
    public void setHidden(boolean value) throws IOException {
        throw new UnsupportedOperationException("setHidden() not supported for SharePoint");
    }

    @Override
    public void setSystem(boolean value) throws IOException {
        throw new UnsupportedOperationException("setSystem() not supported for SharePoint");
    }

    @Override
    public void setArchive(boolean value) throws IOException {
        throw new UnsupportedOperationException("setArchive() not supported for SharePoint");
    }

    @Override
    public boolean isReadOnly() {
        logger.debug("isReadOnly()=false");
        return false;
    }

    @Override
    public boolean isHidden() {
        logger.debug("isHidden()=false");
        return false;
    }

    @Override
    public boolean isArchive() {
        logger.debug("isArchive()=false");
        return false;
    }

    @Override
    public boolean isSystem() {
        logger.debug("isSystem()=false");
        return false;
    }

}
