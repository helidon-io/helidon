# Build Container Images with Jib

This guide describes how to build container images for Helidon applications using Jib and Maven.

## What You Need

|                                                     |
|-----------------------------------------------------|
| About 10 minutes                                    |
| [Helidon Prerequisites](../about/prerequisites.md) |

## Creating a Docker Image Using Jib

[Jib](https://github.com/GoogleContainerTools/jib) is a java tool chain for building Docker images for Java applications. It is integrated with Maven and Gradle and uses a [distro-less](https://github.com/GoogleContainerTools/distroless) base image to produce small images.

Jib does not require the `docker` command or the Docker daemon, there is no need to solve the Docker-in-Docker problem in order to build Docker images as part of your continuous integration.

> [!NOTE]
> The `docker` command is only required for local usage when registering images in your local Docker registry.

The example below shows how to build an image and register it in the local registry using the `jib-maven-plugin`.

Add the following plugin declaration to your pom.xml:

``` xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>0.10.1</version>
    <configuration>
        <to>
            <image>jib-${project.artifactId}</image>
            <tags>
                <tag>${project.version}</tag>
                <tag>latest</tag>
            </tags>
        </to>
        <container>
            <!-- good defaults intended for containers -->
            <jvmFlags>
                <jmxFlag>-server</jmxFlag>
                <jmxFlag>-Djava.awt.headless=true</jmxFlag>
                <jmxFlag>-XX:+UnlockExperimentalVMOptions</jmxFlag>
                <jmxFlag>-XX:+UseCGroupMemoryLimitForHeap</jmxFlag>
                <jmxFlag>-XX:InitialRAMPercentage=50</jmxFlag>
                <jmxFlag>-XX:MinRAMPercentage=50</jmxFlag>
                <jmxFlag>-XX:MaxRAMPercentage=50</jmxFlag>
                <jmxFlag>-XX:+UseG1GC</jmxFlag>
            </jvmFlags>
            <mainClass>${mainClass}</mainClass>
            <ports>
                <port>8080</port>
            </ports>
        </container>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>dockerBuild</goal>
            </goals>
            <phase>package</phase>
        </execution>
    </executions>
</plugin>
```

> [!NOTE]
> By default, Jib uses [distroless/java](https://github.com/GoogleContainerTools/distroless/tree/master/java) as the base image. You can override the default with configuration see the [documentation](https://github.com/GoogleContainerTools/jib/blob/v0.10.1-maven/jib-maven-plugin/README.md#extended-usage)

*Package the updated application*

``` bash
mvn package
```

*Run the image*

``` bash
docker run --rm -p 8080:8080 jib-helidon-quickstart-se
```

*Ping the application*

``` bash
curl -X GET http://localhost:8080/greet
```

*Take a look at the image size*

``` bash
docker images jib-quickstart-se:latest
```

``` bash
REPOSITORY          TAG           IMAGE ID      CREATED        SIZE
jib-quickstart-se   latest        384aebda5594  48 years ago   124MB 
```

- Ignore the fact that it says the image was created 48 years ago. Refer to the [Jib FAQ](https://github.com/GoogleContainerTools/jib/blob/v0.10.1-maven/jib-maven-plugin/../docs/faq.md#why-is-my-image-created-48-years-ago) for explanations.

> [!NOTE]
> the Jib image is smaller because of the use of a distroless base image.
