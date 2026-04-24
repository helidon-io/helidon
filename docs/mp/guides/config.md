# Helidon MP Config Guide

This guide describes how to create a sample MicroProfile (MP) project that can be used to run some basic examples using both default and custom configuration with Helidon MP.

## What You Need

For this 20 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

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

## Getting Started with Configuration

Helidon provides a very flexible and comprehensive configuration system, offering you many application configuration choices. You can include configuration data from a variety of sources using different formats, like JSON and YAML. Furthermore, you can customize the precedence of sources and make them optional or mandatory. This guide introduces Helidon MP configuration and demonstrates the fundamental concepts using several examples. Refer to [Helidon Config](../../mp/config/introduction.md) for the full configuration concepts documentation.

### Create a Sample Helidon MP Project

Use the Helidon MP Maven archetype to create a simple project that can be used for the examples in this guide.

*Run the Maven archetype:*

```bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-mp \
    -Dpackage=io.helidon.examples.quickstart.mp
```

*The project will be built and run from the `helidon-quickstart-mp` directory:*

```bash
cd helidon-quickstart-mp
```

### Default Configuration

Helidon has an internal configuration, so you are not required to provide any configuration data for your application, though in practice you most likely would. By default, that configuration can be overridden from three sources: system properties, environment variables, and the contents of `META-INF/microprofile-config.properties`. For example, if you specify a custom server port in `META-INF/microprofile-config.properties` then your server will listen on that port.

A main class is also required to start up the server and run the application. By default, the Quickstart sample project uses the built-in Helidon main class. In this guide you want to use your own main class, so you have more control over the server initialization. First define your own `Main`:

*src/main/java/io/helidon/examples/quickstart/mp/Main.java*

```java
public final class Main {

    private Main() {
    } 

    public static void main(final String[] args) {
        Server server = startServer();
        System.out.println("http://localhost:" + server.port() + "/greet");
    }

    static Server startServer() {
        return Server.create().start(); 
    }

}
```

In this class, a `main` method is defined which starts the Helidon MP server and prints out a message with the listen address.

- Notice that this class has an empty no-args constructor to make sure this class cannot be instantiated.
- The MicroProfile server is started with the default configuration.

Next change the project’s `pom.xml` to use your main class:

*pom.xml*

```xml
<properties>
    <mainClass>io.helidon.examples.quickstart.mp.Main</mainClass>
</properties>
```

This property will be used to set the `Main-Class` attribute in the application jar’s MANIFEST.

In your application code, Helidon uses the default configuration when you create a `Server` object without a custom `Config` object. See the following code from the project you created.

*View `Main#startServer`:*

```java
static Server startServer() {
    return Server.create().start(); 
}
```

- There is no `Config` object being used during server creation, so the default configuration is used.

### Source Precedence for Default Configuration

In order to properly configure your application using configuration sources, you need to understand the precedence rules that Helidon uses to merge your configuration data. By default, Helidon will use the following sources in precedence order:

1.  Java system properties
2.  Environment variables
3.  Properties specified in `META-INF/microprofile-config.properties`

Each of these sources specify configuration properties in Java Property format (key/value), like `color=red`. If any of the Helidon required properties are not specified in one of these source, like `server.port`, then Helidon will use a default value.

> [!NOTE]
> Because environment variable names are restricted to alphanumeric characters and underscores, Helidon adds aliases to the environment configuration source, allowing entries with dotted and/or hyphenated keys to be overridden. For example, this mapping allows an environment variable named "APP_GREETING" to override an entry key named "app.greeting". In the same way, an environment variable named "APP_dash_GREETING" will map to "app-greeting". See [Microprofile Config Specifications](https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html) for more information.

The following examples will demonstrate the default precedence order.

#### Default Configuration Resource

Change a configuration parameter in the default configuration resource file, `META-INF/microprofile-config.properties`. There are no environment variable or system property overrides defined.

*Change `app.greeting` in the `META-INF/microprofile-config.properties` from `Hello` to `HelloFromMPConfig`:*

```properties
app.greeting=HelloFromMPConfig
```

*Build the application, skipping unit tests, then run it:*

```bash
mvn package -DskipTests=true
java -jar target/helidon-quickstart-mp.jar
```

*Run the curl command in a new terminal window and check the response:*

```bash
curl http://localhost:8080/greet
```

```json
{
  "message": "HelloFromMPConfig World!" 
}
```

- The new `app.greeting` value in `META-INF/microprofile-config.properties` is used.

##### Environment Variable Override

An environment variable has a higher precedence than the configuration properties file.

*Set the environment variable and restart the application:*

```bash
export APP_GREETING=HelloFromEnvironment
java -jar target/helidon-quickstart-mp.jar
```

*Invoke the endpoint*

```bash
curl http://localhost:8080/greet
```

*JSON response:*

```json
{
  "message": "HelloFromEnvironment World!" 
}
```

- The environment variable took precedence over the value in `META-INF/microprofile-config.properties`.

##### System Property Override

A system property has a higher precedence than environment variables.

*Restart the application with a system property. The `app.greeting` environment variable is still set:*

```bash
java -Dapp.greeting="HelloFromSystemProperty"  -jar target/helidon-quickstart-mp.jar
```

*Invoke the endpoint*

```bash
curl http://localhost:8080/greet
```

*JSON response:*

```json
{
  "message": "HelloFromSystemProperty World!" 
}
```

- The system property took precedence over both the environment variable and `META-INF/microprofile-config.properties`.

## Accessing Config within an Application

The examples in this section will demonstrate how to access that config data at runtime. Your application uses the `Config` object to access the in-memory tree, retrieving config data.

The generated project already accesses configuration data in the `GreetingProvider` class as follows:

*View the following code from `GreetingProvider.java`:*

```java
@ApplicationScoped 
public class GreetingProvider {
    private final AtomicReference<String> message = new AtomicReference<>(); 

    @Inject
    public GreetingProvider(@ConfigProperty(name = "app.greeting") String message) {   
        this.message.set(message);
    }

    String getMessage() {
        return message.get();
    }

    void setMessage(String message) {
        this.message.set(message);
    }
}
```

- This class is application scoped so a single instance of `GreetingProvider` will be shared across the entire application.
- Define a thread-safe reference that will refer to the message member variable.
- The value of the configuration property `app.greeting` is injected into the `GreetingProvider`. constructor as a `String` parameter named `message`.

### Injecting at Field Level

You can inject configuration at the field level as shown below. Use the `volatile` keyword since you cannot use `AtomicReference` with field level injection.

*Update the `meta-config.yaml` with the following contents:*

```yaml
sources:
  - type: "classpath"
    properties:
      resource: "META-INF/microprofile-config.properties"  
```

- This example only uses the default classpath source.

*Update the following code from `GreetingProvider.java`:*

```java
@ApplicationScoped
public class GreetingProvider {

    @Inject
    @ConfigProperty(name = "app.greeting") 
    private volatile String message; 

    String getMessage() {
        return message;
    }

    void setMessage(String message) {
        this.message = message;
    }
}
```

- Inject the value of `app.greeting` into the `GreetingProvider` object.
- Define a class member variable to hold the greeting.

*Build and run the application, then invoke the endpoint*

```bash
curl http://localhost:8080/greet
```

*JSON response:*

```json
{
  "message": "HelloFromMPConfig World!"
}
```

### Injecting the Config Object

You can inject the `Config` object into the class and access it directly as shown below.

*Replace the `GreetingProvider` class:*

```java
@ApplicationScoped
public class GreetingProvider {
    private final AtomicReference<String> message = new AtomicReference<>();

    @Inject 
    public GreetingProvider(Config config) {
        String message = config.get("app.greeting").asString().get(); 
        this.message.set(message);
    }

    String getMessage() {
        return message.get();
    }

    void setMessage(String message) {
        this.message.set(message);
    }
}
```

- Inject the `Config` object into the `GreetingProvider` object.
- Get the `app.greeting` value from the `Config` object and set the member variable.

*Build and run the application, then invoke the endpoint*

```bash
curl http://localhost:8080/greet
```

*JSON response:*

```json
{
  "message": "HelloFromMPConfig World!"
}
```

### Navigating the Config Tree

Helidon offers a variety of methods to access in-memory configuration. These can be categorized as *key access* or *tree navigation*. You have been using *key access* for all the examples to this point. For example `app.greeting` is accessing the `greeting` child node of the `app` parent node.

This simple example below demonstrates how to access a child node as a detached configuration subtree.

*Create a file `config-file.yaml` in the `helidon-quickstart-mp` directory and add the following contents:*

```yaml
app:
  greeting:
    sender: Joe
    message: Hello-from-config-file.yaml
```

*Update the `meta-config.yaml` with the following contents:*

```yaml
sources:
  - type: "classpath"
    properties:
      resource: "META-INF/microprofile-config.properties"
  - type: "file"
    properties:
      path: "./config-file.yaml"
```

*Replace `GreetingProvider` class with the following code:*

```java
@ApplicationScoped
public class GreetingProvider {
    private final AtomicReference<String> message = new AtomicReference<>();
    private final AtomicReference<String> sender = new AtomicReference<>();

    @Inject
    Config config;

    public void onStartUp(@Observes @Initialized(ApplicationScoped.class) Object init) {
        Config appNode = config.get("app.greeting"); 
        message.set(appNode.get("message").asString().get());  
        sender.set(appNode.get("sender").asString().get());   
    }

    String getMessage() {
        return sender.get() + " says " + message.get();
    }

    void setMessage(String message) {
        this.message.set(message);
    }
}
```

- Get the configuration subtree where the `app.greeting` node is the root.
- Get the value from the `message` `Config` node.
- Get the value from the `sender` `Config` node.

*Build and run the application, then invoke the endpoint*

```bash
curl http://localhost:8080/greet
```

*JSON response:*

```json
{
  "message": "Joe says Hello-from-config-file.yaml World!"
}
```

## Integration with Kubernetes

The following example uses a Kubernetes ConfigMap to pass the configuration data to your Helidon application deployed to Kubernetes. When the pod is created, Kubernetes will automatically create a local file within the container that has the contents of the configuration file used for the ConfigMap. This example will create the file at `/etc/config/config-file.properties`.

*Update the `Main` class and replace the `buildConfig` method:*

```java
private static Config buildConfig() {
    return Config.builder()
            .sources(
                    file("/etc/config/config-file.properties").optional(), 
                    classpath("META-INF/microprofile-config.properties")) 
            .build();
}
```

- The `app.greeting` value will be fetched from `/etc/config/config-file.properties` within the container.
- The server port is specified in `META-INF/microprofile-config.properties` within the `helidon-quickstart-mp.jar`.

*Update the following code from `GreetingProvider.java`:*

```java
@ApplicationScoped
public class GreetingProvider {

    @Inject
    @ConfigProperty(name = "app.greeting") 
    private volatile String message; 

    String getMessage() {
        return message;
    }

    void setMessage(String message) {
        this.message = message;
    }
}
```

*Build and run the application, then invoke the endpoint*

```bash
curl http://localhost:8080/greet
```

*JSON response:*

```json
{
  "message": "HelloFromConfigFile World!"
}
```

*Stop the application and build the docker image:*

```bash
docker build -t helidon-config-mp .
```

*Generate a ConfigMap from `config-file.properties`:*

```bash
kubectl create configmap helidon-configmap --from-file config-file.properties
```

*View the contents of the ConfigMap:*

```bash
kubectl get configmap helidon-configmap -o yaml
```

```yaml
apiVersion: v1
data:
  config-file.properties: |  
    app.greeting=HelloFromConfigFile
kind: ConfigMap
```

- The file `config-file.properties` will be created within the Kubernetes container.
- The `config-file.properties` file will have this single property defined.

*Create the Kubernetes YAML specification, named `k8s-config.yaml`, with the following contents:*

```yaml
kind: Service
apiVersion: v1
metadata:
  name: helidon-config 
  labels:
    app: helidon-config
spec:
  type: NodePort
  selector:
    app: helidon-config
  ports:
    - port: 8080
      targetPort: 8080
      name: http
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: helidon-config
spec:
  replicas: 1 
  selector:
    matchLabels:
      app: helidon-config
  template:
    metadata:
      labels:
        app: helidon-config
        version: v1
    spec:
      containers:
        - name: helidon-config
          image: helidon-config-mp
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          volumeMounts:
            - name: config-volume
              mountPath: /etc/config 
      volumes:
        - name: config-volume
          configMap:
            # Provide the name of the ConfigMap containing the files you want
            # to add to the container
            name:  helidon-configmap 
```

- A service of type `NodePort` that serves the default routes on port `8080`.
- A deployment with one replica of a pod.
- Mount the ConfigMap as a volume at `/etc/config`. This is where Kubernetes will create `config-file.properties`.
- Specify the ConfigMap which contains the configuration data.

*Create and deploy the application into Kubernetes:*

```bash
kubectl apply -f ./k8s-config.yaml
```

*Get the service information:*

```bash
kubectl get service/helidon-config
```

```bash
NAME             TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
helidon-config   NodePort   10.99.159.2   <none>        8080:31143/TCP   8s 
```

- A service of type `NodePort` that serves the default routes on port `31143`.

*Verify the configuration endpoint using port `31143`, your port will likely be different:*

```bash
curl http://localhost:31143/greet
```

*JSON response:*

```json
{
  "message": "HelloFromConfigFile World!" 
}
```

- The greeting value from `/etc/config/config-file.properties` within the container was used.

You can now delete the Kubernetes resources that were just created during this example.

*Delete the Kubernetes resources:*

```bash
kubectl delete -f ./k8s-config.yaml
kubectl delete configmap  helidon-configmap
```

## Summary

This guide has demonstrated how to use basic Helidon configuration features. For more information about using the advanced Helidon configuration features, including mutability support and extensions, see [Helidon Configuration](../../mp/config/introduction.md).

## References

Refer to the following references for additional information:

- [MicroProfile Config specification](https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html)
- [MicroProfile Config Javadoc](https://download.eclipse.org/microprofile/microprofile-config-3.1/apidocs)
- [Helidon Javadoc](/apidocs/index.html?overview-summary.html)
