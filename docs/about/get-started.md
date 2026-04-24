# Get Started

## Quick Start

If you want to jump in and give Helidon a try make sure you satisfy the
[prerequisites](#system-requirements). Then:

1. [Download the Helidon CLI](cli.md)
2. Create a Helidon SE project
   ```shell
   helidon init --batch -Dflavor=se -Dapp-type=quickstart
   ```
3. Build it 
   ```shell
   cd quickstart-se; mvn clean install
   ```
4. Run it
   ```shell
   java -jar target/quickstart-se.jar
   ```
5. Try it
   - Visit: http://localhost:8080/greet

----------

Congratulations! You now have a simple service up and running using Helidon.
If you prefer to use MicroProfile APIs replace `se` with `mp` in the commands above.
If you prefer not to install the Helidon CLI you can use the [Helidon Starter](https://helidon.io/starter).

## In More Detail

Helidon is a framework for developing microservices in Java. As such, you don’t install Helidon itself. Instead, you install the tools necessary to create an environment suitable for developing Helidon projects and applications.

## System Requirements

You must have Java and Maven installed on your system to use Helidon.

| Requirement                                                                                                 | Description                                  |
|-------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi)                                                         | Helidon requires Maven 3.8+.                 |

> [!NOTE]
> Most of Helidon's examples use Maven, but Helidon can also be used with [Gradle](../se/guides/gradle-build.md).

*Verify System Requirements*

```bash
java -version
mvn --version
```
### Set the `JAVA_HOME` Environment Variable

Make sure you set the `JAVA_HOME` environment variable.

*Set `JAVA_HOME` on Linux*

```bash
# Enter the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-25
```

*Set `JAVA_HOME` on macOS*

```bash
# Enter the appropriate path to your JDK
export JAVA_HOME=`/usr/libexec/java_home -v 25`
```

*Set `JAVA_HOME` on Windows*

```cmd
# Enter the appropriate path to your JDK
setx JAVA_HOME=C:\\PROGRA~1\\Java\\jdk-25
```

## Next Steps

Now that your environment is set up, you can get started with Helidon. Try out the Helidon MP and Helidon SE Quick Start tutorials to build your first Helidon project and application.

- [Helidon MP Quick Start](../mp/guides/quickstart.md)
- [Helidon SE Quick Start](../se/guides/quickstart.md)

> [!TIP]
> Read [About Helidon](introduction.md) to help you understand the differences between Helidon MP and Helidon SE.
