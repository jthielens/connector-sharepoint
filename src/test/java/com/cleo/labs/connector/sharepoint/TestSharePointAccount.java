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
            assertEquals(ConnectorException.Category.fileNonExistentOrNoAccess, e.getCategory().get());
        }

        BasicFileAttributeView docs = client.getAttributes("Documents");
        assertTrue(docs.readAttributes().isDirectory());

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

}
