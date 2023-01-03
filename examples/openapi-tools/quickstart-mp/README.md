# Helidon OpenAPI Generator example for Helidon MP server and client

The goal of this example is to show how a user can easily create a Helidon MP server or client from an OpenAPI document using OpenAPI Generator.  

Here we will show the steps that a user has to do to create Helidon MP server and client using OpenAPI Generator and what has to be done to make the generated server and client fully functional.

For generation of our projects we will use `openapi-generator-cli.jar` that can be downloaded from the maven repository (instructions and other options can be found [here](https://openapi-generator.tech/docs/installation)) and OpenAPI document `quickstart.yaml` that can be found next to this `README.md`.

## Build, prepare and run the Helidon MP server

To generate Helidon MP server at first we create `mp-server` folder and then inside it we run the following command where `path-to-generator` is the directory where you downloaded the generator CLI JAR file and `path-to-openapi-doc` is the folder where `quickstart.yaml` is located:
```bash
java -jar path-to-generator/openapi-generator-cli.jar \
          generate \
          -g java-helidon-server \  
          --library mp \
          -i path-to-openapi-doc/quickstart.yaml
```

When this command finishes its work in the folder `mp-server` we will find the generated project where the most interesting parts are located inside `api` and `model` packages.
The package `api` contains interfaces that represent endpoints for our server and implementations with stubs for them. 
These implementations we need to change to implement our business logic.
The package `model` contains classes that represent transport objects that will be used by our endpoints to receive requests and send responses.

Let's change a little class `MessageServiceImpl` for our example :
1) Add annotation `@ApplicationScoped` to this class.
2) Add field that will contain default message for our endpoints :
```java
    private final AtomicReference<Message> defaultMessage = new AtomicReference<>();
```
3) Add default constructor to the class :
```java
    public MessageServiceImpl() {
        Message message = new Message();
        message.setMessage("World");
        message.setGreeting("Hello");
        defaultMessage.set(message);
    }
```
4) Replace implementation of the method `public Message getDefaultMessage()` by this:
```java
        return defaultMessage.get();
```
5) Replace implementation of the method `public Message getMessage(@PathParam("name") String name)` by this:
```java
        Message result = new Message();
        return result.message(name).greeting(defaultMessage.get().getGreeting());
```
6) Replace implementation of the method `public Response updateGreeting(@Valid @NotNull Message message)` by this:
```java
        defaultMessage.set(message);
        Response.status(Response.Status.NO_CONTENT).build();
```

To run the application : 

With JDK17+
```bash
mvn package
java -jar target/openapi-java-server.jar
```

To check that server works as expected run the following `curl` commands :

```
curl -X GET http://localhost:8080/greet
{"message":"World","greeting":"Hello"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Joe","greeting":"Hello"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola", "message":"Lisa"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet
{"message":"Lisa","greeting":"Hola"}
```

## Build, prepare and run the Helidon MP client

The second part of this example is generating MicroProfile Rest Client that will communicate with the server that we have just created.

To generate Helidon MP client at first we create `mp-client` folder and then inside it we run the following command where `path-to-generator` is the directory where you downloaded the generator CLI JAR file and `path-to-openapi-doc` is the folder where `quickstart.yaml` is located:
```bash
java -jar path-to-generator/openapi-generator-cli.jar \
          generate \
          -g java-helidon-client \  
          --library mp \
          -i path-to-openapi-doc/quickstart.yaml
```

When this command finishes its work in the folder `mp-client` we will find the generated project. 
As with server project there is the most interesting part are located inside `api` and `model` packages.
The package `api` contains interfaces that represent endpoints to our server.
The package `model` contains classes that represent transport objects that will be used to communicate with the server.

You can use the generated MP client artifact in either of two ways:

 - as a library - One or more other client projects can depend on the client artifact and use its generated classes.
 - as a client program itself - Add some code to the generated project to make it a client program and not just a library.

This example illustrates the second approach. We create a second server (at port 8081) which accepts greeting requests and, acting as a client, forwards them to the first service and returns the responses from the first service as its own.

To make our client application fully functional let's add some classes, dependencies and files to the project.

1) Let's add a class `MessageService` that will use `MessageApi` interface to interact with the server :
```java
@Path("/greet")
@ApplicationScoped
public class MessageService {

    @Inject
    @RestClient
    private MessageApi messageApi;


    @GET
    @Produces({"application/json"})
    public Message getDefaultMessage() throws ApiException {
        return messageApi.getDefaultMessage();
    }

    @GET
    @Path("/{name}")
    @Produces({"application/json"})
    public Message getMessage(@PathParam("name") String name) throws ApiException {
        return messageApi.getMessage(name);
    }

    @PUT
    @Path("/greeting")
    @Consumes({"application/json"})
    public void updateGreeting(Message message) throws ApiException {
        messageApi.updateGreeting(message);
    }
}
```

2) Create the directory `src/main/resources/META-INF`.
3) Add file `beans.xml` inside the folder `META-INF` :
```xml
<?xml version="1.0" encoding="UTF-8"?>

<beans xmlns="http://xmlns.jcp.org/xml/ns/javaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
                           http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd"
       version="2.0"
       bean-discovery-mode="annotated">
</beans>
```
4) Add file `microprofile-config.properties` inside the folder `META-INF` :
```properties
# Microprofile server properties
server.port=8081
server.host=0.0.0.0
```
5) Add dependency in `pom.xml` :
```xml
<dependency>
    <groupId>io.helidon.microprofile.bundles</groupId>
    <artifactId>helidon-microprofile-core</artifactId>
</dependency>
```
6) In the file `MessageApi` replace the line:
```java
@RegisterRestClient
```
to
```java
@RegisterRestClient(baseUri="http://localhost:8080")
```

To run the application :

With JDK17+
```bash
mvn package
java -jar target/openapi-java-client.jar
```

To check that the client works as expected and process all the requests using our server run the following `curl` commands :

```
curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hi", "message":"Mike"}' http://localhost:8081/greet/greeting

curl -X GET http://localhost:8081/greet
{"message":"Mike","greeting":"Hi"}

curl -X GET http://localhost:8081/greet/Joe
{"message":"Joe","greeting":"Hi"}
```

## Update applications

To keep the server and the client up to date according to the OpenApi document, we can use the maven plugin `openapi-generator-maven-plugin`.

Add these lines to the `pom.xml` of our server :
```xml
    <profiles>
        <profile>
            <id>openapi</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.openapitools</groupId>
                        <artifactId>openapi-generator-maven-plugin</artifactId>
                        <version>${version.openapi.generator.maven.plugin}</version>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>generate</goal>
                                </goals>
                                <configuration>
                                    <inputSpec>${project.basedir}/src/main/resources/META-INF/openapi.yml</inputSpec>
                                    <generatorName>java-helidon-server</generatorName>
                                    <library>mp</library>
                                    <output>${project.basedir}</output>
                                    <configOptions>
                                        <fullProject>false</fullProject>
                                    </configOptions>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
```

For the client application :
1) Copy OpenApi document `path-to-openapi-doc/quickstart.yaml` to the folder `META-INF` and rename it to `openapi.yaml`.
2) Add these lines to the `pom.xml` of our client :
```xml
    <profiles>
        <profile>
            <id>openapi</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.openapitools</groupId>
                        <artifactId>openapi-generator-maven-plugin</artifactId>
                        <version>${version.openapi.generator.maven.plugin}</version>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>generate</goal>
                                </goals>
                                <configuration>
                                    <inputSpec>${project.basedir}/src/main/resources/META-INF/openapi.yml</inputSpec>
                                    <generatorName>java-helidon-client</generatorName>
                                    <library>mp</library>
                                    <output>${project.basedir}</output>
                                    <configOptions>
                                        <fullProject>false</fullProject>
                                    </configOptions>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
```

Also add the following to the `<properties>` in the `pom.xml` file:
```xml
<version.openapi.generator.maven.plugin>6.2.1</version.openapi.generator.maven.plugin>
```

The version `6.2.1` was the first version where Helidon generators were added, so if more modern versions of this plugin are exist you can choose one of them.

To run the generator during your build, invoke the profile: `mvn clean package -P openapi`.

It should also be added that the `fullProject` option was used in the plugin configuration. 
If it set to true, it will generate all files; if set to false, it will only generate API files. 
If unspecified, the behavior depends on whether a project exists or not: if it does not, same as true; if it does, same as false. 
So keep in mind that regenerating will overwrite your customized `MessageService` or `Message` files and you will need to add the customization again after regenerating.
Note that test files are never overwritten.
