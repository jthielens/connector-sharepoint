package com.cleo.labs.connector.sharepoint;

import java.io.IOException;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;

import com.cleo.connector.api.helper.Logger;
import com.independentsoft.share.File;

/**
 * SharePoint file attribute views
 */
public class SharePointFileAttributes implements DosFileAttributes, DosFileAttributeView {
    private File file;
    private Logger logger;

    public SharePointFileAttributes(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public FileTime lastModifiedTime() {
        logger.debug(String.format("lastModifiderTime()=%s", file.getLastModifiedTime().toString()));
        return FileTime.fromMillis(file.getLastModifiedTime().getTime());
    }

    @Override
    public FileTime lastAccessTime() {
        logger.debug(String.format("lastAccessTime()=%s", file.getLastModifiedTime().toString()));
        return FileTime.fromMillis(file.getLastModifiedTime().getTime());
    }

    @Override
    public FileTime creationTime() {
        logger.debug(String.format("creationTime()=%s", file.getLastModifiedTime().toString()));
        return FileTime.fromMillis(file.getLastModifiedTime().getTime());
    }

    @Override
    public boolean isRegularFile() {
        logger.debug("isRegularFile()=true");
        return true; // files are regular files
    }

    @Override
    public boolean isDirectory() {
        logger.debug("isDirectory()=false");
        return false; // files are regular files
    }

    @Override
    public boolean isSymbolicLink() {
        logger.debug("isSymbolicLink()=false");
        return false; // files are regular files
    }

    @Override
    public boolean isOther() {
        logger.debug("isOther()=false");
        return false; // files are regular files
    }

    @Override
    public long size() {
        logger.debug(String.format("size()=%d", file.getLength()));
        return file.getLength();
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
