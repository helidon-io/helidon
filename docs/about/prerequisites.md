# Get Started

Helidon is a framework for developing microservices in Java. As such, you don’t install Helidon itself. Instead, you install the tools necessary to create an environment suitable for developing Helidon projects and applications.

## System Requirements

You must have Java and Maven installed on your system to use Helidon. Depending on how you plan to deploy your services, you may need to install Docker and kubectl as well.

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../about/kubernetes.md)). |

Requirements

If you use Windows, see [Helidon on Windows](windows.md) for additional prerequisites.

*Verify System Requirements*

``` bash
java -version
mvn --version
docker --version
kubectl version
```

### Set the `JAVA_HOME` Environment Variable

Make sure you set the `JAVA_HOME` environment variable.

*Set `JAVA_HOME` on Linux*

``` bash
# Enter the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

*Set `JAVA_HOME` on macOS*

``` bash
# Enter the appropriate path to your JDK
export JAVA_HOME=`/usr/libexec/java_home -v 21`
```

*Set `JAVA_HOME` on Windows*

``` cmd
# Enter the appropriate path to your JDK
setx JAVA_HOME=C:\\PROGRA~1\\Java\\jdk-21
```

## Next Steps

Now that your environment is set up, you can get started with Helidon. Try out the Helidon MP and Helidon SE Quick Start tutorials to build your first Helidon project and application.

- [Helidon MP Quick Start](../mp/guides/quickstart.md)
- [Helidon SE Quick Start](../se/guides/quickstart.md)

> [!TIP]
> Read [About Helidon](introduction.md) to help you understand the differences between Helidon MP and Helidon SE.
