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
15:40:44.240 INFO  [main] i.h.examples.logging.slf4j.Main - Starting up startup
15:40:44.241 INFO  [main] i.h.examples.logging.slf4j.Main - Using JUL logger startup
15:40:44.245 INFO  [pool-1-thread-1] i.h.examples.logging.slf4j.Main - Running on another thread propagated
15:40:44.395 INFO  [features-thread] io.helidon.common.HelidonFeatures - Helidon SE 2.2.0 features: [Config, WebServer]
15:40:44.538 INFO  [nioEventLoopGroup-2-1] io.helidon.webserver.NettyWebServer - Channel '@default' started: [id: 0x8e516487, L:/0:0:0:0:0:0:0:0:8080]
```

The output is also logged into `helidon.log`.

# Running as jar

Build this application:
```shell script
mvn clean package
```

Run from command line:
```shell script
java -jar target/helidon-examples-logging-sfl4j.jar
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
```shell script
./target/helidon-examples-logging-sfl4j
```
