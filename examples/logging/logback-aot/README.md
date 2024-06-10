Slf4j/Logback Example
---

This example shows how to use slf4j with MDC backed by Logback
 using Helidon API.

The example moves all Java Util Logging to slf4j and supports more advance configuration of logback.

# AOT (native image)
To support native image, we need to use a different logback configuration at build time and at runtime.
To achieve this, we bundle `logback.xml` on classpath, and then have `logback-runtime.xml` with 
configuration that requires started threads (which is not supported at build time).

The implementation will re-configure logback (see method `setupLogging` in `Main.java).

To see that configuration works as expected at runtime, change the log level of our package to `debug`.
Within 30 seconds the configuration should be reloaded, and next request will have two more debug messages.

Expected output should be similar to the following (for both hotspot and native):
```text
15:40:44.240 [INFO ] [io.helidon.examples.logging.logback.aot.Main.logging:128] Starting up startup
15:40:44.241 [INFO ] [o.slf4j.jdk.platform.logging.SLF4JPlatformLogger.performLog:151] Using System logger startup
15:40:44.245 [INFO ] [io.helidon.examples.logging.logback.aot.Main.log:146] Running on another thread propagated
15:40:44.395 [INFO ] [o.slf4j.jdk.platform.logging.SLF4JPlatformLogger.performLog:151] Helidon 4.0.0-SNAPSHOT features: [Config, Encoding, Media, WebServer] 
15:40:44.538 [INFO ] [o.slf4j.jdk.platform.logging.SLF4JPlatformLogger.performLog:151] Started all channels in 15 milliseconds. 647 milliseconds since JVM startup. Java 20.0.1+9-29 propagated
```

The output is also logged into `helidon.log`.

# Running as jar

Build this application:
```shell
mvn clean package
```

Run from command line:
```shell
java -jar target/helidon-examples-logging-slf4j-aot.jar
```

Execute endpoint:
```shell
curl -i http://localhost:8080
```

# Running as native image
You must use GraalVM with native image installed as your JDK,
or you can specify an environment variable `GRAALVM_HOME` that points
to such an installation.

Build this application:
```shell script
mvn clean package -Pnative-image
```

Run from command line:
```shell
./target/helidon-examples-logging-slf4j-aot
```
