package com.cleo.labs.connector.sharepoint;

import com.cleo.connector.api.property.ConnectorPropertyException;

/**
 * A configuration wrapper around a {@link SharePointConnectorClient}
 * instance and its {@link SharePointConnectorSchema}, exposing bean-like
 * getters for the schema properties converted to their usable forms:
 * <table border="1">
 *   <tr><th>Property</th><th>Stored As</th><th>Returned as</th></tr>
 *   <tr><td>Storage Account Name</td><td>String</td><td>String</td></tr>
 *   <tr><td>Access Key</td><td>String</td><td>String</td></tr>
 *   <tr><td>Endpoint Suffix</td><td>String (core.windows.net by default)</td><td>String</td>
 *   <tr><td>Connection String</td><td>computed</td><td>String</td></tr>
 *   <tr><td>Container</td><td>String</td><td>String</td></tr>
 * </table>
 */
public class SharePointConnectorConfig {
    private SharePointConnectorClient client;
    private SharePointConnectorSchema schema;

    /**
     * Constructs a configuration wrapper around a {@link SharePointConnectorClient}
     * instance and its {@link SharePointConnectorSchema}, exposing bean-like
     * getters for the schema properties converted to their usable forms.
     * @param client the SharePointConnectorClient
     * @param schema its SharePointConnectorSchema
     */
    public SharePointConnectorConfig(SharePointConnectorClient client, SharePointConnectorSchema schema) {
        this.client = client;
        this.schema = schema;
    }
 
    /**
     * Gets the Service URL property.
     * @return the Service URL
     * @throws ConnectorPropertyException
     */
    public String getServiceURL() throws ConnectorPropertyException {
        return schema.serviceURL.getValue(client);
    }

    /**
     * Gets the Username property
     * @return the Username
     * @throws ConnectorPropertyException
     */
    public String getUsername() throws ConnectorPropertyException {
        return schema.username.getValue(client);
    }

    /**
     * Gets the Password property
     * @return the Password
     * @throws ConnectorPropertyException
     */
    public String getPassword() throws ConnectorPropertyException {
        return schema.password.getValue(client);
    }

    /**
     * Gets the Domain property
     * @return the Domain
     * @throws ConnectorPropertyException
     */
    public String getDomain() throws ConnectorPropertyException {
        return schema.domain.getValue(client);
    }

}
