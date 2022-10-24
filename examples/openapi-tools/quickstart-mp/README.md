# Helidon OpenAPI Generator example for Helidon MP server and client

The goal of this example is to show how a user can easily create Helidon MP server or client from OpenAPI document using OpenAPI Generator.  

Here we will show the steps that a user has to do to create Helidon MP server and client using OpenAPI Generator and what has to be done to make the generated server and client fully functional.

For generation of our projects we will use `openapi-generator-cli.jar` that can be downloaded form the maven repository (instructions and other options can be found [here](https://openapi-generator.tech/docs/installation)) and OpenAPI document `quicksrart.yaml` that can be found next to this `README.md`.

## Build, prepare and run the Helidon MP server

To generate Helidon MP server at first we create `mp-server` folder and then inside it we run the following command :
```bash
java -jar openapi-generator-cli.jar \
          generate \
          -g java-helidon-server \  
          --library mp \
          -i quickstart.yaml
```

When this command finishes its work in the folder `mp-server` we will find the generated project where the most interesting parts are located inside `api` and `model` packages.
The package `api` contains interfaces that represent endpoints for our server and implementations with stubs for them. 
These implementations we need to change to implement our business logic.
The package `model` contains classes that represent transport objects that will be used by our endpoints to receive requests and sent responses.

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
4) Replace implementation of the method `public Response getDefaultMessage()` by this:
```java
        return Response.ok().entity(defaultMessage.get()).build();
```
5) Replace implementation of the method `public Response getMessage(@PathParam("name") String name)` by this:
```java
        defaultMessage.get().setMessage(name);
        return Response.ok().entity(defaultMessage.get()).build();
```
6) Replace implementation of the method `public Response updateGreeting(@Valid @NotNull Message message)` by this:
```java
        defaultMessage.set(message);
        return Response.status(Response.Status.NO_CONTENT).build();
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

To generate Helidon MP client at first we create `mp-client` folder and then inside it we run the following command :
```bash
java -jar openapi-generator-cli.jar \
          generate \
          -g java-helidon-client \  
          --library mp \
          -i quickstart.yaml
```

When this command finishes its work in the folder `mp-client` we will find the generated project. 
As with server project there is the most interesting part are located inside `api` and `model` packages.
The package `api` contains interfaces that represent endpoints to our server.
The package `model` contains classes that represent transport objects that will be used to communicate with the server.
To make our client application fully functional let's add some classes, dependencies and files to the project.

1) Let's add a class `MessageService` that will use `MessageApi` interface to interact with the server :
```java
@Path("/greet")
@ApplicationScoped
public class MessageService {

    @Inject
    @RestClient
    MessageApi messageApi;


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

2) Add class `RestApplication` that extends `Application` :
```java
@ApplicationScoped
@ApplicationPath("")
public class RestApplication extends Application {

}
```
3) Create `META-INF` folder inside resource directory.
4) Add `beans.xml` inside `META-INF` folder :
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
5) Add `microprofile-config.properties` inside `META-INF` folder :
```properties
# Microprofile server properties
server.port=8081
server.host=0.0.0.0
```
6) Add dependency in `pom.xml` :
```xml
<dependency>
    <groupId>io.helidon.microprofile.bundles</groupId>
    <artifactId>helidon-microprofile-core</artifactId>
</dependency>
```

To run the application :

With JDK17+
```bash
mvn package
java -jar target/openapi-java-client.jar
```

To check that client works as expected and process all the request using our server run the following `curl` commands :

```
curl -X GET http://localhost:8081/greet

curl -X GET http://localhost:8081/greet/Joe

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola", "message":"Lisa"}' http://localhost:8081/greet/greeting
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
1) Copy OpenApi document `openapi.yaml` to the `META-INF` folder.
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

