package com.cleo.labs.connector.sharepoint;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.command.ConnectorCommandName;
import com.cleo.connector.api.command.ConnectorCommandOption;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.shell.interfaces.IConnector;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.connector.testing.Commands;
import com.cleo.labs.connector.testing.StringCollector;
import com.cleo.labs.connector.testing.StringSource;
import com.cleo.labs.connector.testing.TestConnector;
import com.cleo.labs.connector.testing.TestConnectorHost;
import com.independentsoft.share.ServiceException;

public class TestSharePointAccount {

    private static SharePointConnectorClient setupClient() {
        SharePointConnectorSchema sharePointSchema = new SharePointConnectorSchema();
        sharePointSchema.setup();
        IConnector connector = new TestConnector(System.err)
                .set("SharePointURL", TestConfigValues.URL)
                .set("UserName", TestConfigValues.USERNAME)
                .set("Password", TestConfigValues.PASSWORD)
                .set(CommonProperty.EnableDebug.name(), Boolean.TRUE.toString());
        SharePointConnectorClient client = new SharePointConnectorClient(sharePointSchema);
        IConnectorHost connectorHost = new TestConnectorHost(client);
        client.setup(connector, sharePointSchema, connectorHost);

        return client;
    }

    @Ignore
    @Test
    public void testTestConnectorHost() {
        SharePointConnectorClient client = setupClient();
        assertEquals("sharept", client.getHost().getSchemeName());
        assertFalse(client.getHost().isSupported(ConnectorCommandName.DELETE));
        assertFalse(client.getHost().isSupported(ConnectorCommandName.CONNECT));
        assertFalse(client.getHost().isSupported("no such thing"));
        assertTrue(client.getHost().isSupported("DIR"));
        assertEquals(TestConfigValues.URL, client.getHost().getPropertyValue("SharePointURL").orElse(""));
        assertEquals("true", client.getHost().getPropertyValue(CommonProperty.EnableDebug.name()).orElse(""));
    }

    @Ignore
    @Test
    public void testDir() {
        SharePointConnectorClient client = setupClient();
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
    public void testAttrs() throws ServiceException, ConnectorException, IOException {
        SharePointConnectorClient client = setupClient();

        try {
            client.getAttributes("not a real file");
            fail("this file should not exist");
        } catch (ConnectorException e) {
            assertEquals(ConnectorException.Category.fileNonExistentOrNoAccess, e.getCategory().orElse(null));
        }

        BasicFileAttributeView docs = client.getAttributes("Documents");
        assertTrue(docs.readAttributes().isDirectory());

        // this should cause the details to be cached
        Commands.dir("Documents").go(client);
        // so this should fetch from cache -- to really test this need to inspect the debug output
        BasicFileAttributeView file = client.getAttributes("Documents/library.pptx");
        assertTrue(file.readAttributes().isRegularFile());

    }

    @Ignore
    @Test
    public void testCreateContainer() {
        SharePointConnectorClient client = setupClient();
        ConnectorCommandResult result;

        String container = "container-"+UUID.randomUUID().toString();
        // make a new container
        result = Commands.mkdir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
        // make it again -- it's existing, but still should be ok
        result = Commands.mkdir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
        // now delete it
        result = Commands.rmdir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
        // delete it (non existing) should also be ok
        result = Commands.rmdir(container).go(client);
        assertEquals(Status.Success, result.getStatus());
    }

    @Test
    public void testRoundTrip() throws Exception {
        SharePointConnectorClient client = setupClient();
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
        SharePointConnectorClient client = setupClient();
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
    public void testPutUnique() {
        SharePointConnectorClient client = setupClient();
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
    public void testPutOverwrite() {
        SharePointConnectorClient client = setupClient();
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
    public void testRename() {
        SharePointConnectorClient client = setupClient();
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
        assertEquals(TEST+".rename", entries.get(0).getPath());
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
        assertEquals(TEST+".rename", entries.get(0).getPath());
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
