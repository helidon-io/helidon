JUL Example
---

This example shows how to use Java Util Logging with MDC
 using Helidon API.
 
The example can be built using GraalVM native image as well.

# Running as jar

Build this application:
```shell script
mvn clean package
```

Run from command line:
```shell script
java -jar target/helidon-examples-logging-jul.jar
```

Expected output should be similar to the following:
```text
2020.11.19 15:37:28 INFO io.helidon.common.LogConfig Thread[main,5,main]: Logging at initialization configured using classpath: /logging.properties ""
2020.11.19 15:37:28 INFO io.helidon.examples.logging.jul.Main Thread[main,5,main]: Starting up "startup"
2020.11.19 15:37:28 INFO io.helidon.examples.logging.jul.Main Thread[pool-1-thread-1,5,main]: Running on another thread "propagated"
2020.11.19 15:37:28 INFO io.helidon.common.HelidonFeatures Thread[features-thread,5,main]: Helidon SE 2.2.0 features: [Config, WebServer] ""
2020.11.19 15:37:28 INFO io.helidon.webserver.NettyWebServer Thread[nioEventLoopGroup-2-1,10,main]: Channel '@default' started: [id: 0x8a5f5634, L:/0:0:0:0:0:0:0:0:8080] ""
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
./target/helidon-examples-logging-jul
```

Expected output should be similar to the following:
```text
2020.11.19 15:38:14 INFO io.helidon.common.LogConfig Thread[main,5,main]: Logging at runtime configured using classpath: /logging.properties ""
2020.11.19 15:38:14 INFO io.helidon.examples.logging.jul.Main Thread[main,5,main]: Starting up "startup"
2020.11.19 15:38:14 INFO io.helidon.examples.logging.jul.Main Thread[pool-1-thread-1,5,main]: Running on another thread "propagated"
2020.11.19 15:38:14 INFO io.helidon.common.HelidonFeatures Thread[features-thread,5,main]: Helidon SE 2.2.0 features: [Config, WebServer] ""
2020.11.19 15:38:14 INFO io.helidon.webserver.NettyWebServer Thread[nioEventLoopGroup-2-1,10,main]: Channel '@default' started: [id: 0x2b929906, L:/0:0:0:0:0:0:0:0:8080] ""
```