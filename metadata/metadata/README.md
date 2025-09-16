Helidon Metadata
-----

This module is responsible for discovering Helidon metadata both when running on "proper" classpath or module path,
and when running on "uber" classpath (i.e. fat jars, shaded jars, etc.).

Important note - Metadata discovery only works on unique file names. As we gather multiple files from different locations,
the file name is the only element we can use for grouping.

# Files

The following files are expected in a single location on classpath, and will not work with shaded jar, unless correctly merged (this will not cause runtime failures):
- `default-media-types.properties` - default media types (there should be exactly one instance of this file used by Helidon)
- `config-metadata.json` - configuration metadata, may be used by tooling to find all configurable options (i.e. from IDE), not used at runtime of Helidon
- `feature-metadata.properties` - deprecated feature metadata


The following files are expected to be under `META-INF/helidon` directory structure.
Each file then lives in a directory structure depending on module type (see JPMS modules and regular modules). 

The following files are expected by Helidon; this list may be extended in the future (`${file-name}` in the location patterns):

- `service-registry.json` - information for Helidon `ServiceRegistry`
- `feature-registry.json` - feature metadata, used at startup and when building native image
- `service.loader` - Java `ServiceLoader` services that should be discovered by Helidon `ServiceRegistry`
- `serial-config.properties` - configuration for Java serialization
- `media-types.properties` - customized media types

All the files mentioned above will also be discovered from `META-INF/helidon` directory (directly) for backward compatibility,
testing, and ease of use when not shading. 
A general rule is that each resource is used just once (i.e. if a duplicate record in manifest exists).

## JPMS modules

Each file will be in a directory structure `META-INF/helidon/${jpms-module-name}/${file-name}`, where:

- `jpms-module-name` is the name of the module (Java module system)
- `file-name` is one of the supported Helidon metadata files

## Regular modules

Each file will be in a directory structure `${package-name}-${unique-id}/${file-name}`, where:

- `package-name` is the package name considered "top" package of the module by Helidon codegen
- `unique-id` is a unique identifier generated at build time by Helidon codegen (i.e. a UUID, or a timestamp)
- `file-name` is one of the supported Helidon metadata files

# Discovery

If there is more than one `META-INF/MANIFEST.MF` files on the classpath, we follow the multiple resources approach;
if there is one or less manifest files, we follow the classpath scanning approach.

## Multiple resources approach

The discovery process:

1. Lookup all `META-INF/helidon/manifest` files
2. Read each file as lines, where lines prefixed with `#` are considered comments, and empty lines are ignored
3. Each line is a location of a metadata file on the classpath (expected to be unique)
4. The metadata module will group all files by file name, to allow other components to discover them

## Classpath scanning approach

The discovery process:

1. Classpath must be either file based or module-file based (i.e. jars and directories), cannot work with custom classloaders
2. We analyze all files under `META-INF/helidon` except for the manifest file
3. The metadata module will group all files by file name, to allow other components to discover them

# Options

The metadata module can have options configured to modify its behavior.
Options may also be configured when invoking `Metadata` programmatically.

The following options are currently supported:

- `mode` - which mode to use, see Metadata.Mode, defaults to `AUTO`, can be changed using a Java system property
            (i.e. `-Dio.helidon.metadata.mode=RESOURCES`)
- `location` - location from which we start classpath discovery, when used, defaults to `META-INF/helidon`
  (this is expected to contain the manifest file, and possible metadata files that are not part of the manifest for backward
  compatibility)
- `metadata-files` - list of file names of metadata files to find (comma separated list, defaults to all supported files)
- `manifest-file` - location of the main manifest file
