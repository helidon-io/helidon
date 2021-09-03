
# Building Docs

If you want to do a local build of the documentation and javadocs
so you can preview them, here is what you do.

## Build  

If you're behind a proxy, you'll want to set `JAVA_TOOL_OPTIONS` to
pass proxy system properties to javadoc:

```
export JAVA_TOOL_OPTIONS="-DproxyHost=yourproxy.com -DproxyPort=80 -DnonProxyHosts=localhost|127.0.0.1"
```

First do a priming build to ensure your local Maven repo cache is populated with
Helidon artifacts. These are needed to build the aggregated javadocs.

```
mvn clean install -DskipTests
```

Next build the docs (including aggregated javadocs):

```
mvn site
```

Without javadocs:

```
mvn site -Dmaven.javadoc.skip
```

## View the docs

The built docs will be in the top level target directory:

```
cd target/site
python -m SimpleHTTPServer 8000
```

View them in your browser:

```
http://localhost:8000
```

