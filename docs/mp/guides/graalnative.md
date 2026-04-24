# GraalVM Native Images

This guide describes how to build a GraalVM native image for a Helidon MP application.

## Introduction

[Native images](https://www.graalvm.org/jdk21/reference-manual/native-image/) are ahead-of-time compiled Java code that result in a self contained native executable. When used appropriately native images have dramatically faster startup and lower runtime memory overhead compared to a Java VM.

In this guide you will learn how to build a native image locally on your machine, as well as using Docker.

## What You Need

For this 10 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |
| [GraalVM for JDK 21](https://www.graalvm.org/release-notes/JDK_21/) | `native-image` support requires GraalVM for JDK 21. When running in the Graal JVM (not native-image) Helidon supports GraalVM for JDK 21 or newer. |

*Verify Prerequisites*

```bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

```bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

## Install GraalVM and the Native Image Command

After [downloading and installing](https://www.graalvm.org/jdk21//docs/getting-started/) GraalVM, set the `GRAALVM_HOME` environment variable to point at your GraalVM installation, or use the GraalVM installation as your Java home.

```bash
# Your path might be different
export GRAALVM_HOME=/usr/local/graalvm-jdk-21+35.1/Contents/Home/
```

Then verify:

```bash
$GRAALVM_HOME/bin/java -version
$GRAALVM_HOME/bin/native-image --version
```

## Generate the Project

Generate the project using the Helidon MP Quickstart Maven archetype.

```bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-mp \
    -Dpackage=io.helidon.examples.quickstart.mp
```

The archetype generates a Maven project in your current directory (for example, `helidon-quickstart-mp`). Change into this directory and build.

```bash
cd helidon-quickstart-mp
mvn package
```

At this point you can run the application using the JVM:

```bash
java -jar target/helidon-quickstart-mp.jar
```

In another shell test an endpoint:

```bash
curl -X GET http://localhost:8080/greet
```

The application should respond with `{"message":"Hello World!"}`

Now stop the running application (by pressing Ctrl+C).

For more information about the Quickstart application and other endpoints it supports see the [Helidon MP Quickstart Guide](../../mp/guides/quickstart.md).

## Building a Native Image

You can build a native executable in 2 different ways:

- With a local installation of GraalVM
- Using Docker

### Local build

Make sure you have GraalVM locally installed:

```bash
$GRAALVM_HOME/bin/native-image --version
```

Build the native image using the native image profile:

```bash
mvn package -Pnative-image
```

> [!TIP]
> This uses the `org.graalvm.buildtools:native-maven-plugin` to perform the native compilation using your installed copy of GraalVM. It might take a while to complete.

Once it completes start the application using the native executable (no JVM!):

```bash
./target/helidon-quickstart-mp
```

Yep, it starts fast. You can exercise the application’s endpoints as before.

### Multi-stage Docker build

Build the "native" Docker image

```bash
docker build -t helidon-quickstart-mp-native -f Dockerfile.native .
```

> [!TIP]
> This does a full build inside the Docker container. The first time you run it, it will take a while because it is downloading all of the Maven dependencies and caching them in a Docker layer. Subsequent builds will be much faster as long as you don’t change the `pom.xml` file. If the pom is modified then the dependencies will be re-downloaded.

Start the application:

```bash
docker run --rm -p 8080:8080 helidon-quickstart-mp-native:latest
```

Again, it starts fast. You can exercise the application’s endpoints as before.

## When should I use Native Images?

Native images are ideal for applications with high horizontal scalability requirements where the ability to rapidly scale out to numerous instances is important.

That said, native images do have some [limitations](https://www.graalvm.org/jdk21/reference-manual/native-image/metadata/Compatibility/), and for long running applications where startup and footprint are less of a priority, the Java SE HotSpot VM might be more appropriate.

For information about creating custom Java runtime images see [Custom Runtime Images with `jlink`](../../mp/guides/jlink-image.md).

> [!NOTE]
> When building Helidon using native-image, we check features on classpath, and warn if there is a problem or restriction of support
