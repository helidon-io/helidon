# Helidon Javadocs

Aggregated javadocs for the Helidon Components.

## Requirements

The Maven `sources.jar` are required to aggregate the javadocs.
When building locally against SNAPSHOT, you can generate the `sources.jar` for
 all components by doing a top level build with `-Psources`.

## Building the javadocs

```bash
# Cd to the project root
$ mvn install  -Psources
$ cd javadocs ; mvn generate-sources
```

## Resolving links behind proxy

If you are behind a proxy and get errors from javadoc that it can't
resolve links then you need to pass your proxy settings to javadoc.
One easy way to do this is to use the `JAVA_TOOL_OPTIONS` environment
variable to set the http proxy Java system properties.

If you've already set these system properties in `MAVEN_OPTS`:

```bash
echo $MAVEN_OPTS
-Dhttp.proxyHost=yourproxy.com -Dhttp.proxyPort=80
-Dhttp.nonProxyHosts=127.0.0.1|localhost|yourdomain.com
-Dhttps.proxyHost=yourproxy.com -Dhttps.proxyPort=80
-Dhttps.nonProxyHosts=127.0.0.1|localhost|yourdomain.com
```

then you can just do:

```bash
export JAVA_TOOL_OPTIONS="${MAVEN_OPTS}"
```
