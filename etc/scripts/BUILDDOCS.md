
# Building Docs

If you want to do a local build of the documentation and javadocs
so you can preview them, here is what you do.

## Build  

If you're behind a proxy, you'll want to set `JAVA_TOOL_OPTIONS` to
pass proxy system properties to javadoc:

```
export JAVA_TOOL_OPTIONS="-DproxyHost=yourproxy.com -DproxyPort=80 -DnonProxyHosts=localhost|127.0.0.1"
```

Then build:

```
mvn clean install -Pjavadoc,docs -DskipTests
```

## View the docs

Go to the docs target directory and start an HTTP server:

```
cd docs/target/docs
python -m SimpleHTTPServer 8000
```

View them in your browser:
```
http://localhost:8000

