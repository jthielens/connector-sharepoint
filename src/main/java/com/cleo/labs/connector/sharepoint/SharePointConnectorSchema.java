package com.cleo.labs.connector.sharepoint;

import java.io.IOException;

import com.cleo.connector.api.ConnectorConfig;
import com.cleo.connector.api.annotations.Client;
import com.cleo.connector.api.annotations.Connector;
import com.cleo.connector.api.annotations.Info;
import com.cleo.connector.api.annotations.Property;
import com.cleo.connector.api.interfaces.IConnectorProperty;
import com.cleo.connector.api.interfaces.IConnectorProperty.Attribute;
import com.cleo.connector.api.property.CommonProperties;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.api.property.PropertyBuilder;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

@Connector(scheme = "sharept", description = "Microsoft SharePoint")
@Client(SharePointConnectorClient.class)
public class SharePointConnectorSchema extends ConnectorConfig {

    @Property
    final IConnectorProperty<String> serviceURL = new PropertyBuilder<>("SharePointURL", "")
            .setDescription("The SharePoint service URL.")
            .setRequired(true)
            .setAllowedInSetCommand(false)
            .build();

    @Property
    final IConnectorProperty<String> username = new PropertyBuilder<>("UserName", "")
            .setDescription("The SharePoint user name.")
            .setRequired(true)
            .setAllowedInSetCommand(false)
            .build();

    @Property
    final IConnectorProperty<String> password = new PropertyBuilder<>("Password", "")
            .setDescription("The user's password.")
            .setRequired(true)
            .setAllowedInSetCommand(false)
            .addAttribute(Attribute.Password)
            .build();

    @Property
    final IConnectorProperty<Boolean> enableDebug = CommonProperties.of(CommonProperty.EnableDebug);

    @Info
    protected static String info() throws IOException {
        return Resources.toString(SharePointConnectorSchema.class.getResource("info.txt"), Charsets.UTF_8);
    }
}