# Helidon OpenAPI Generator example for Helidon SE server and client

The goal of this example is to show how a user can easily create a Helidon SE server or client from an OpenAPI document using OpenAPI Generator.  

Here we will show the steps that a user has to do to create Helidon SE server and client using OpenAPI Generator and what has to be done to make the generated server and client fully functional.

For generation of our projects we will use `openapi-generator-cli.jar` that can be downloaded from the maven repository (instructions and other options can be found [here](https://openapi-generator.tech/docs/installation)) and OpenAPI document `quickstart.yaml` that can be found next to this `README.md`.

## Build, prepare and run the Helidon SE server

To generate Helidon SE server at first we create `se-server` folder and then inside it we run the following command where `path-to-generator` is the directory where you downloaded the generator CLI JAR file and `path-to-openapi-doc` is the folder where `quickstart.yaml` is located:
```bash
java -jar path-to-generator/openapi-generator-cli.jar \
          generate \
          -g java-helidon-server \  
          --library se \
          -i path-to-openapi-doc/quickstart.yaml
```

When this command finishes its work in the folder `se-server` we will find the generated project where the most interesting parts are located inside `api` and `model` packages.
The package `api` contains interfaces that represent endpoints for our server and implementations with stubs for them. 
These implementations we need to change to implement our business logic.
The package `model` contains classes that represent transport objects that will be used by our endpoints to receive requests and send responses.

Let's change a little class `MessageServiceImpl` for our example :
1) Add field that will contain default message for our endpoints :
```java
    private final AtomicReference<Message> defaultMessage = new AtomicReference<>();
```
2) Add default constructor to the class :
```java
    public MessageServiceImpl() {
        Message message = new Message();
        message.setMessage("World");
        message.setGreeting("Hello");
        defaultMessage.set(message);
    }
```
3) Replace implementation of the method `public void getDefaultMessage(ServerRequest request, ServerResponse response)` by this:
```java
        response.send(defaultMessage.get());
```
4) Replace implementation of the method `public void getMessage(ServerRequest request, ServerResponse response)` by this:
```java
        String name = request.path().param("name");
        Message result = new Message();
        result.setMessage(name);
        result.setGreeting(defaultMessage.get().getGreeting());
        response.send(result);
```
5) Replace implementation of the method `public void updateGreeting(ServerRequest request, ServerResponse response, Message message)` by this:
```java
        if (message.getGreeting() == null) {
        Message jsonError = new Message();
        jsonError.setMessage("No greeting provided");
        response.status(Http.Status.BAD_REQUEST_400)
        .send(jsonError);
        return;
        }
        defaultMessage.set(message);
        response.status(Http.Status.NO_CONTENT_204).send();
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

## Build, prepare and run the Helidon SE client

The second part of this example is generating Helidon Webclient that will communicate with the server that we have just created.

To generate Helidon SE Webclient at first we create `se-client` folder and then inside it we run the following command where `path-to-generator` is the directory where you downloaded the generator CLI JAR file and `path-to-openapi-doc` is the folder where `quickstart.yaml` is located:
```bash
java -jar path-to-generator/openapi-generator-cli.jar \
          generate \
          -g java-helidon-client \  
          --library se \
          -i path-to-openapi-doc/quickstart.yaml
```

When this command finishes its work in the folder `se-client` we will find the generated project. 
As with the server project the most interesting parts are located inside `api` and `model` packages and `ApiClient` class.
The package `api` contains interfaces that represent endpoints to our server and implementations for them.
The package `model` contains classes that represent transport objects that will be used to communicate with the server.
`ApiClient` class represents configuration and utility class for `WebClient` that is used to connect to our server.

You can use the generated SE client artifact in either of two ways:

 - as a library - One or more other client projects can depend on the client artifact and use its generated classes.
 - as a client program itself - Add some code to the generated project to make it a client program and not just a library.

This example illustrates the second approach. We create a second server (at port 8081) which accepts greeting requests and, acting as a client, forwards them to the first service and returns the responses from the first service as its own.

To make our client application fully functional let's add some classes, dependencies and files to the project.

1) Add  to the `pom.xml` :
```xml
    <properties>
        <mainClass>org.openapitools.client.Main</mainClass>
    </properties>

    <dependency>
        <groupId>io.helidon.webserver</groupId>
        <artifactId>helidon-webserver</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.config</groupId>
        <artifactId>helidon-config-yaml</artifactId>
    </dependency>
```

2) Let's add a class `MessageService` to the `api` package that will use `MessageApi` and `ApiClient` to interact with the server :
```java
package org.openapitools.client.api;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import org.openapitools.client.ApiClient;
import org.openapitools.client.model.Message;

public class MessageService implements Service {

    private final MessageApi api;

    public MessageService() {
        ApiClient apiClient = ApiClient.builder().build();
        api = MessageApiImpl.create(apiClient);
    }

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/greet", this::getDefaultMessage);
        rules.get("/greet/{name}", this::getMessage);
        rules.put("/greet/greeting", Handler.create(Message.class, this::updateGreeting));
    }

    /**
     * GET /greet : Return a worldly greeting message..
     *
     * @param request  the server request
     * @param response the server response
     */
    public void getDefaultMessage(ServerRequest request, ServerResponse response) {
        api.getDefaultMessage()
           .webClientResponse()
           .flatMapSingle(serverResponse -> serverResponse.content().as(Message.class))
           .thenAccept(response::send);
    }

    /**
     * GET /greet/{name} : Return a greeting message using the name that was provided..
     *
     * @param request  the server request
     * @param response the server response
     */
    public void getMessage(ServerRequest request, ServerResponse response) {
        String name = request.path().param("name");
        api.getMessage(name)
           .webClientResponse()
           .flatMapSingle(serverResponse -> serverResponse.content().as(Message.class))
           .thenAccept(response::send);
    }

    /**
     * PUT /greet/greeting : Set the greeting to use in future messages..
     *
     * @param request  the server request
     * @param response the server response
     * @param message  Message for the user
     */
    public void updateGreeting(ServerRequest request, ServerResponse response, Message message) {
        api.updateGreeting(message)
           .webClientResponse()
           .thenAccept(content -> response.status(Http.Status.NO_CONTENT_204).send());
    }
}
```

3) Add class `Main` that will be the main class for our client :
```java
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        WebServer server = WebServer.builder(createRouting(config))
                                    .config(config.get("server"))
                                    .addMediaSupport(JacksonSupport.create())
                                    .build();

        Single<WebServer> webserver = server.start();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        webserver.thenAccept(ws -> {
                     System.out.println("WEB server is up! http://localhost:8081");
                     ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                 })
                 .exceptionallyAccept(t -> {
                     System.err.println("Startup failed: " + t.getMessage());
                     t.printStackTrace(System.err);
                 });

        return webserver;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return routing configured with JSON support, a health check, and a service
     * @param config configuration of this server
     */
    private static Routing createRouting(Config config) {

        return Routing.builder()
                      .register("/", new MessageService())
                      .build();
    }
}
```

4) Create the directory `src/main/resources/`. Create `application.yaml` in that directory with the following content:
```yaml
server:
  port: 8081
  host: localhost
```

To run the application :

With JDK17+
```bash
mvn package
java -jar target/openapi-java-client.jar
```

To check that client works as expected and process all the request using our server run the following `curl` commands :

```
curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola", "message":"Lisa"}' http://localhost:8081/greet/greeting

curl -X GET http://localhost:8081/greet
{"message":"Lisa","greeting":"Hola"}

curl -X GET http://localhost:8081/greet/Joe
{"message":"Joe","greeting":"Hola"}
```

## Update applications

To keep the server and the client up to date according to the OpenApi document, we can use the maven plugin.

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
                                    <library>se</library>
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
1) Copy the OpenApi document `path-to-openapi-doc/quickstart.yaml` to the folder `resources` and rename it to `openapi.yaml`.
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
                                    <inputSpec>${project.basedir}/src/main/resources/openapi.yml</inputSpec>
                                    <generatorName>java-helidon-client</generatorName>
                                    <library>se</library>
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
