package com.cleo.labs.connector.testing;

import java.io.PrintStream;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorConfig;
import com.cleo.connector.api.annotations.Client;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.google.common.io.ByteStreams;

public class TestConnectorClient {

    private static final PrintStream NULL_PRINT_STREAM = new PrintStream(ByteStreams.nullOutputStream());

    public static class Builder {
        private Class<? extends ConnectorConfig> schemaClass;
        private Class<?> schemaValues = null;
        private PrintStream logger = NULL_PRINT_STREAM;
        private boolean debug = false;
        public Builder(Class<? extends ConnectorConfig> schemaClass) {
            this.schemaClass = schemaClass;
        }
        public Builder values(Class<?> schemaValues) {
            this.schemaValues = schemaValues;
            return this;
        }
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }
        public Builder logger(PrintStream logger) {
            this.logger = logger == null ? NULL_PRINT_STREAM : logger;
            return this;
        }
        public ConnectorClient build() throws Exception {
            ConnectorConfig schema = schemaClass.newInstance();
            schema.setup();
            Client clientAnnotation = schemaClass.getAnnotation(Client.class);
            Class<? extends ConnectorClient> clientClass = clientAnnotation.value();
            ConnectorClient client = clientClass.getDeclaredConstructor(schemaClass).newInstance(schema);
            IConnectorHost connectorHost = new TestConnectorHost(client);
            TestConnector connector = new TestConnector(logger)
                    .set(CommonProperty.EnableDebug.name(), String.valueOf(debug));
            if (schemaValues != null) {
                connector.set(schemaValues);
            }
            client.setup(connector, schema, connectorHost);
            return client;
        }
    }

    public static Builder of(Class<? extends ConnectorConfig> schemaClass) {
        return new Builder(schemaClass);
    }

    private TestConnectorClient() {
    }

}
