Log4j Example
---

This example shows how to use log4j with MDC (`ThreadContext`)
 using Helidon API.
 
The example moves all Java Util Logging to log4j.

The example can be built using GraalVM native image as well.

# Running as jar

Build this application:
```shell script
mvn clean package
```

Run from command line:
```shell script
java -jar target/helidon-examples-logging-log4j.jar
```

Expected output should be similar to the following:
```text
2020-11-19 15:44:48,561 main INFO Registered Log4j as the java.util.logging.LogManager.
15:44:48.596 INFO  [main] io.helidon.examples.logging.log4j.Main - Starting up "startup"
15:44:48.598 INFO  [main] io.helidon.examples.logging.log4j.Main - Using JUL logger "startup"
15:44:48.600 INFO  [pool-2-thread-1] io.helidon.examples.logging.log4j.Main - Running on another thread "propagated"
15:44:48.704 INFO  [features-thread] io.helidon.common.HelidonFeatures - Helidon SE 2.2.0 features: [Config, WebServer] ""
15:44:48.801 INFO  [nioEventLoopGroup-2-1] io.helidon.webserver.NettyWebServer - Channel '@default' started: [id: 0xa215c23d, L:/0:0:0:0:0:0:0:0:8080] ""
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
./target/helidon-examples-logging-log4j
```

*In native image, we can only replace loggers initialized after reconfiguration of logging system
This unfortunately means that Helidon logging would not be available*

Expected output should be similar to the following:
```text
15:47:53.033 INFO  [main] io.helidon.examples.logging.log4j.Main - Starting up "startup"
15:47:53.033 INFO  [main] io.helidon.examples.logging.log4j.Main - Using JUL logger "startup"
15:47:53.033 INFO  [pool-2-thread-1] io.helidon.examples.logging.log4j.Main - Running on another thread "propagated"
```