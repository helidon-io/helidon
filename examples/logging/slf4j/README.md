Slf4j Example
---

This example shows how to use slf4j with MDC
 using Helidon API.

The example moves all Java Util Logging to slf4j
 
The example can be built using GraalVM native image as well.

Expected output should be similar to the following (for both hotspot and native):
```text
15:40:44.240 INFO  [main] i.h.examples.logging.slf4j.Main - Starting up startup
15:40:44.241 INFO  [main] i.h.examples.logging.slf4j.Main - Using JUL logger startup
15:40:44.245 INFO  [pool-1-thread-1] i.h.examples.logging.slf4j.Main - Running on another thread propagated
15:40:44.395 INFO  [features-thread] io.helidon.common.HelidonFeatures - Helidon SE 2.2.0 features: [Config, WebServer]
15:40:44.538 INFO  [nioEventLoopGroup-2-1] io.helidon.webserver.NettyWebServer - Channel '@default' started: [id: 0x8e516487, L:/0:0:0:0:0:0:0:0:8080]
```

# Running as jar

Build this application:
```shell script
mvn clean package
```

Run from command line:
```shell script
java -jar target/helidon-examples-logging-sfl4j.jar
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
