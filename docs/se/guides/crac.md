# CRaC

This guide describes how to create a CRaC snapshot for a Helidon SE application.

## Introduction

CRaC - Coordinated Restore at Checkpoint

> [!NOTE]
> CRaC support is a preview feature. The feature shown here is subject to change, and will be finalized in a future release of Helidon.

## What You Need

For this 10 minute tutorial, you will need the following:

|  |  |
|----|----|
| Linux/x64 or Linux/ARM64 | While CRaC snapshotting can be simulated on MacOS or Windows, full CRaC functionality is only available on Linux/x64 and Linux/ARM64. |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Azul Zulu JDK CRaC 21+](https://www.azul.com/downloads/?version=java-21-lts&package=jdk-crac#zulu) | Zulu Warp CRaC engine allows snapshotting without elevated privileges |

## Install JDK with CRaC support

There are two JDK builds with CRaC support as of now to choose from.

- [Azul Zulu](https://www.azul.com/downloads/?version=java-21-lts&package=jdk-crac#zulu)
- [BellSoft Liberica JDK](https://bell-sw.com/pages/downloads/?package=jdk-crac&version=java-21)

In this example we will use Azul implementation with Warp CRaC engine. Warp CRaC engine allows creating snapshots without elevated privileges. That not only simplifies the example, but it is very practical for K8s usage.

Use [SDKMAN!](https://sdkman.io) to install Azul JDK with Warp CRaC engine:

``` bash
sdk install java 23.0.1.crac-zulu
```

## Generate the Project

Generate the project using the Helidon SE Quickstart Maven archetype.

``` bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-se \
    -Dpackage=io.helidon.examples.quickstart.se
```

The archetype generates a Maven project in your current directory (for example, `helidon-quickstart-se`). Change into this directory and build.

``` bash
cd helidon-quickstart-se
```

Add dependency for Helidon CRaC support to `pom.xml`. This allows Helidon to properly close and reopen resources which would normally break snapshot creation.

``` xml
<dependency>
    <groupId>io.helidon.integrations.crac</groupId>
    <artifactId>helidon-integrations-crac</artifactId>
</dependency>
```

Build the project.

``` bash
mvn package
```

Check if you are using Java build with CRaC support.

``` bash
➜  helidon-quickstart-se java -version
openjdk version "23.0.1" 2024-10-15
OpenJDK Runtime Environment Zulu23.30+13-CRaC-CA (build 23.0.1)
OpenJDK 64-Bit Server VM Zulu23.30+13-CRaC-CA (build 23.0.1, mixed mode, sharing)
```

At this point you can run the application using the CRaC aware JVM:

``` bash
java -XX:CRaCEngine=warp -XX:CRaCCheckpointTo=./target/cr -jar target/helidon-quickstart-se.jar
```

> [!TIP]
> If you hit `Unrecognized VM option` at this point, check if you are using correct JVM with CRaC support.

You should see in the output that Helidon SE has started with CRaC feature enabled.

``` bash
Helidon SE 4.4.0-SNAPSHOT features: [CRaC, Config, Encoding, Health, Media, Metrics, Observe, Registry, WebServer]
[0x3f87bd99] http://0.0.0.0:8080 bound for socket '@default'
Started all channels in 9 milliseconds. 521 milliseconds since JVM startup. Java 23.0.1
WEB server is up! http://localhost:8080/simple-greet
```

In another shell test an endpoint:

``` bash
curl -X GET http://localhost:8080/greet
```

The application should respond with `{"message":"Hello World!"}`

For more information about the Quickstart application and other endpoints it supports see the [Helidon SE Quickstart Guide](../../se/guides/quickstart.md).

## Creating snapshot

In another shell trigger the snapshot creation with [jcmd](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html) command `JDK.checkpoint`:

``` bash
jcmd $(jcmd | grep helidon-quickstart-se.jar | awk '{print $2}') JDK.checkpoint
```

``` bash
warp: Checkpoint 138991 to ./target/cr
warp: Checkpoint successful!
[1]    138991 killed     java -XX:CRaCEngine=warp -XX:CRaCCheckpointTo=./target/cr -jar
```

``` bash
➜  helidon-quickstart-se ls -la ./target/cr
total 124M
-rw------- 1 frank frank 74M Feb  1 19:12 core.img
```

### Restoring from snapshot

Run following command to restore your application from saved snapshot.

``` bash
java -XX:CRaCEngine=warp -XX:CRaCRestoreFrom=./target/cr
```

> [!TIP]
> If you hit `Unrecognized VM option` at this point, check if you are using correct JDK with CRaC support.

Expected output shows that application restore from snapshot is drastically faster than previous start.

``` bash
➜  helidon-quickstart-se java -XX:CRaCEngine=warp -XX:CRaCRestoreFrom=./target/cr
warp: Restore successful!
[0x21a39da4] http://0.0.0.0:8080 bound for socket '@default'
Restored all channels in 2 milliseconds. 20 milliseconds since JVM snapshot restore. Java 23.0.1
```

Yep, it starts fast. You can exercise the application’s endpoints as before.

### Multi-stage Docker build

Build Docker image with pre-warmed snapshot.

> [!TIP]
> For this example you don’t need Linux OS but docker environment is needed.

Create `Dockerfile.crac` in your project folder with following content.

``` dockerfile
# syntax=docker/dockerfile:1.7-labs
ARG BASE_IMAGE=azul/zulu-openjdk:23-jdk-crac-latest
FROM $BASE_IMAGE AS build
RUN apt-get update && apt-get install -y maven

WORKDIR /helidon

# Create a first layer to cache the "Maven World" in the local repository.
# Incremental docker builds will always resume after that, unless you update
# the pom
ADD pom.xml .
RUN mvn package -Dmaven.test.skip -Declipselink.weave.skip

# Do the Maven build!
# Incremental docker builds will resume here when you change sources
ADD src src
RUN mvn package -DskipTests

FROM build AS checkpoint
ENV ENDPOINT=http://localhost:8080/simple-greet
RUN apt-get update && apt-get install -y curl siege
ENV PATH="$PATH:$JAVA_HOME/bin"

# Copy the binary built in the 1st stage
COPY --from=build /helidon/target/helidon-quickstart-se.jar ./
COPY --from=build /helidon/target/libs ./libs

# We use here-doc syntax to inline the script that will
# start the application, warm it up and checkpoint
RUN <<END_OF_SCRIPT
#!/bin/bash
java -XX:CPUFeatures=generic -XX:CRaCEngine=warp \
    -XX:CRaCCheckpointTo=./cr -jar ./helidon-quickstart-se.jar &
PID=$!
# Wait until the connection is opened
until curl --output /dev/null --silent --fail $ENDPOINT; do
    sleep 0.1;
done
# Warm-up the server by executing 100k requests against it
siege -c 1 -r 100000 -b $ENDPOINT
# Trigger the checkpoint
jcmd ./helidon-quickstart-se.jar JDK.checkpoint
# Wait until the process completes, returning success
# (wait would return exit code 137)
wait $PID || true
END_OF_SCRIPT

FROM $BASE_IMAGE
ENV PATH="$PATH:$JAVA_HOME/bin"
WORKDIR /helidon

# Copy checkpoint creted in the 2st stage
COPY --from=checkpoint /helidon/target/helidon-quickstart-se.jar ./
COPY --from=checkpoint /helidon/target/libs ./libs
COPY --from=checkpoint /helidon/cr ./cr
CMD [ "java", "-XX:CRaCEngine=warp", "-XX:CRaCRestoreFrom=/helidon/cr" ]
```

> [!TIP]
> This does a full build inside the Docker container. The first time you run it, it will take a while because it is downloading all of the Maven dependencies and caching them in a Docker layer. Subsequent builds will be much faster as long as you don’t change the `pom.xml` file. If the pom is modified then the dependencies will be re-downloaded.

Build the application, notice that warmup and snapshot of the application is created during build time in the 2nd stage. For warming up the [siege](https://github.com/JoeDog/siege) load testing utility is used. Dockerfile is based on Radim Vansa’s [article](https://foojay.io/today/warp-the-new-crac-engine) introducing Warp CRaC engine.

``` bash
docker build -t helidon-quickstart-se-crac -f Dockerfile.crac .
```

Start the application directly from snapshot created at build time.

``` bash
docker run --rm -p 8080:8080 helidon-quickstart-se-crac:latest
```

Again, it starts fast. You can exercise the application’s endpoints as before.
