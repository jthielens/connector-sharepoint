# README #

This connector supports Microsoft SharePoint as a plugin connector
for Harmony 5.5.

## TL;DR ##

<!---
The POM for this project creates a ZIP archive intended to be expanded from
the Harmony installation directory (`$CLEOHOME` below).

```
git clone git@github.com:jthielens/connector-sharepoint.git
mvn clean package
cp target/sharepoint-5.5.0.0-SNAPSHOT-distribution.zip $CLEOHOME
cd $CLEOHOME
unzip -o sharepoint-5.5.0.0-SNAPSHOT-distribution.zip
./Harmonyd stop
./Harmonyd start
```
--->

The SharePoint connector is distributed as a ZIP archive named
`sharepoint-5.5.0.0-build-distribution.zip` where `build` is a build number
(of 7 heaxadecial digits).  It can be installed on a Harmony 5.5 server.

To install the connector, expand the archive from the Harmony 5.5 installation
directory (`$CLEOHOME` below) and restart Harmony.

```
cd $CLEOHOME
unzip -o sharepoint-5.5.0.0-build-distribution.zip
./Harmonyd stop
./Harmonyd start
```

When Harmony/VLTrader restarts, you will see a new `Template` in the host tree
under `Connections` > `Generic` > `Generic SHAREPT`.  Select `Clone and Activate`
and a new `SHAREPT` connection (host) will appear on the `Active` tab.

To configure the new `SHARPT` connection, enter the URL to your SharePoint site, your SharePoint user name, and your password in the `BLOB` panel.

Each `SHAREPT` connection corresponds either to an entire SharePoint site.
You may repeat the `Clone and Activate` process, or you may `Clone...` an existing `SHAREPT` connection,
to create connections to additional SharePoint sites.

## Connector Actions ##

Actions configured directly for a SharePoint connection may directly manipulate the
associated container through _commands_.  The following commands (and options)
are supported:

| Command | Options | Description |
|---------|---------|-------------|
| `DIR` _directory_    | &nbsp; | List the contents of a (virtual) directory.  Use `DIR ""` to list contents of the account or container root |
| `GET`&nbsp;_name_&nbsp;_destination_ | `-DEL` | Retrieve the contents of Blob _name_ into _destination_, subsequently deleting the Blob if `-DEL` is set. |
| `PUT` _source_ _name_ | `-APE`<br/>`-DEL`<br/>`-UNI` | Store the contents of _source_ into Blob _name_, subsequently deleting _source_ if `-DEL` is set.  See *Blob Types* below for a discussion of the `-APPend` and `-UNIque` options. |
| `DELETE` _name_ | &nbsp; | Deletes Blob _name_ from the container. |
| `ATTR` _name_ | &nbsp; | Retrieves the attributes of Blob _name_. |
| `MKDIR` _name_ | &nbsp; | Creates a placeholder Block Blob _name_`/` (appending the directory separator if needed). |
| `RMDIR` _name_ | &nbsp; | Deletes a placeholder Block Blob _name_`/` (appending the directory separator if needed) if it exists and no additional Blobs exist with _name_`/` as a prefix. |

