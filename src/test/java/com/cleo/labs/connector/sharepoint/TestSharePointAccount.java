package com.cleo.labs.connector.sharepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.command.ConnectorCommandName;
import com.cleo.connector.api.command.ConnectorCommandOption;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.labs.connector.testing.Commands;
import com.cleo.labs.connector.testing.StringCollector;
import com.cleo.labs.connector.testing.StringSource;
import com.cleo.labs.connector.testing.TestConnectorClient;

public class TestSharePointAccount {

    @Test
    public void testTestConnectorHost() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();
        assertEquals("sharept", client.getHost().getSchemeName());
        assertTrue(client.getHost().isSupported(ConnectorCommandName.DELETE));
        assertFalse(client.getHost().isSupported(ConnectorCommandName.CONNECT));
        assertFalse(client.getHost().isSupported("no such thing"));
        assertTrue(client.getHost().isSupported("DIR"));
        assertEquals(TestConfigValues.SharePointURL, client.getHost().getPropertyValue("SharePointURL").orElse(""));
        assertEquals("true", client.getHost().getPropertyValue(CommonProperty.EnableDebug.name()).orElse(""));
    }

    @Ignore
    @Test
    public void testDir() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();
        ConnectorCommandResult result;

        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        List<Entry> entries = result.getDirEntries().orElse(Collections.emptyList());
        for (Entry e : entries) {
            System.out.println(e);
        }
        assertFalse(entries.isEmpty());
    }

    @Test
    public void testAttrs() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();

        try {
            Commands.attr("not a real file").go(client);
            fail("this file should not exist");
        } catch (ConnectorException e) {
            assertEquals(ConnectorException.Category.fileNonExistentOrNoAccess, e.getCategory().orElse(null));
        }

        BasicFileAttributeView docs = Commands.attr("Documents").go(client);
        assertTrue(docs.readAttributes().isDirectory());

        // this should cause the details to be cached
        Commands.dir("Documents").go(client);
        // so this should fetch from cache -- to really test this need to inspect the debug output
        BasicFileAttributeView file = Commands.attr("Documents/library.pptx").go(client);
        assertTrue(file.readAttributes().isRegularFile());

    }

    @Test
    public void testRoundTrip() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();
        ConnectorCommandResult result;

        String container = "Documents";
        result = Commands.dir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
        List<Entry> entries = result.getDirEntries().orElse(Collections.emptyList());
        for (Entry e : entries) {
            System.out.println(e);
        }
        assertFalse(entries.isEmpty());

        String random = UUID.randomUUID().toString();
        StringSource source = new StringSource(random, StringSource.lorem);
        StringCollector destination = new StringCollector().name(random);

        String path = container+"/"+random;
        result = Commands.put(source, path).go(client);
        assertEquals(Status.Success, result.getStatus());
        result = Commands.get(path, destination).go(client);
        assertEquals(Status.Success, result.getStatus());
        assertEquals(StringSource.lorem, destination.toString());
        result = Commands.delete(path).go(client);
        assertEquals(Status.Success, result.getStatus());

        result = Commands.dir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertFalse(entries.stream().anyMatch((e) -> e.getPath().equals(random)));
    }

    @Test
    public void testMkdirRoundTrip() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();
        ConnectorCommandResult result;

        String container = "Documents";
        result = Commands.dir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
        List<Entry> entries = result.getDirEntries().orElse(Collections.emptyList());
        for (Entry e : entries) {
            System.out.println(e);
        }
        assertFalse(entries.isEmpty());

        String random = UUID.randomUUID().toString();
        String path = container+"/"+random;
        result = Commands.mkdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertTrue(entries.isEmpty());
        result = Commands.rmdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());

        result = Commands.dir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertFalse(entries.stream().anyMatch((e) -> e.getPath().equals(random)));
    }

    @Test
    public void testPutUnique() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();
        ConnectorCommandResult result;
        String container = "Documents";
        String random = UUID.randomUUID().toString();
        String path = container+"/"+random;
        List<Entry> entries;

        // make a new folder for the test
        String TEST = "test.txt";
        result = Commands.mkdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());

        // put the first file
        StringSource source;
        source = new StringSource(TEST, StringSource.lorem);
        result = Commands.put(source, path+"/"+TEST).go(client);
        assertEquals(Status.Success, result.getStatus());

        // one file now
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(1, entries.size());
        entries.forEach((e) -> System.err.println("pass 1: "+e));

        // put the second file
        source = new StringSource(TEST, StringSource.lorem);
        result = Commands.put(source, path+"/"+TEST).option(ConnectorCommandOption.Unique).go(client);
        assertEquals(Status.Success, result.getStatus());

        // two files now
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(2, entries.size());
        entries.forEach((e) -> System.err.println("pass 2: "+e));

        // cleanup the testing folder
        result = Commands.rmdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
    }

    @Test
    public void testPutOverwrite() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();
        ConnectorCommandResult result;
        String container = "Documents";
        String random = UUID.randomUUID().toString();
        String path = container+"/"+random;
        List<Entry> entries;

        // make a new folder for the test
        String TEST = "test.txt";
        result = Commands.mkdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());

        // put the first file
        StringSource source;
        source = new StringSource(TEST, "initial content");
        result = Commands.put(source, path+"/"+TEST).go(client);
        assertEquals(Status.Success, result.getStatus());

        // one file now
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(1, entries.size());
        entries.forEach((e) -> System.err.println("pass 1: "+e));

        // put the second file
        source = new StringSource(TEST, StringSource.lorem);
        result = Commands.put(source, path+"/"+TEST).go(client);
        assertEquals(Status.Success, result.getStatus());

        // still one file
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(1, entries.size());
        entries.forEach((e) -> System.err.println("pass 2: "+e));

        // should be the new content
        StringCollector destination = new StringCollector().name(TEST);
        result = Commands.get(path+"/"+TEST, destination).go(client);
        assertEquals(Status.Success, result.getStatus());
        assertEquals(StringSource.lorem, destination.toString());

        // cleanup the testing folder
        result = Commands.rmdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
    }

    @Test
    public void testRename() throws Exception {
        ConnectorClient client = TestConnectorClient.of(SharePointConnectorSchema.class)
                .logger(System.err)
                .debug(true)
                .values(TestConfigValues.class)
                .build();
        ConnectorCommandResult result;
        String container = "Documents";
        String random = UUID.randomUUID().toString();
        String path = container+"/"+random;
        List<Entry> entries;

        // make a new folder for the test
        String TEST = "test.txt";
        result = Commands.mkdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());

        // put the first file
        StringSource source;
        source = new StringSource(TEST, "initial content");
        result = Commands.put(source, path+"/"+TEST).go(client);
        assertEquals(Status.Success, result.getStatus());

        // one file now
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(1, entries.size());
        entries.forEach((e) -> System.err.println("pass 1: "+e));

        // rename it
        result = Commands.rename(path+"/"+TEST, path+"/"+TEST+".rename").go(client);
        assertEquals(Status.Success, result.getStatus());

        // still one file
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(1, entries.size());
        assertEquals(path+"/"+TEST+".rename", entries.get(0).getPath());
        entries.forEach((e) -> System.err.println("pass 2: "+e));

        // put the second file
        source = new StringSource(TEST, StringSource.lorem);
        result = Commands.put(source, path+"/"+TEST).go(client);
        assertEquals(Status.Success, result.getStatus());

        // rename it (overwrite)
        result = Commands.rename(path+"/"+TEST, path+"/"+TEST+".rename").go(client);
        assertEquals(Status.Success, result.getStatus());

        // still one file
        result = Commands.dir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(1, entries.size());
        assertEquals(path+"/"+TEST+".rename", entries.get(0).getPath());
        entries.forEach((e) -> System.err.println("pass 3: "+e));

        // should be the new content
        StringCollector destination = new StringCollector().name(TEST);
        result = Commands.get(path+"/"+TEST+".rename", destination).go(client);
        assertEquals(Status.Success, result.getStatus());
        assertEquals(StringSource.lorem, destination.toString());

        // cleanup the testing folder
        result = Commands.rmdir(path).go(client);
        assertEquals(Status.Success, result.getStatus());
    }
}
