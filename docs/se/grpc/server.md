# gRPC Server

## Overview

The Helidon gRPC server provides a framework for building [gRPC](http://grpc.io/) applications. While it supports deploying any standard gRPC service that implements the `io.grpc.BindableService` interface—including those generated from Protobuf IDL files—it also allows a degree of customization.

Using the Helidon gRPC framework to implement your services offers several advantages:

- Unified programming model: You can define both HTTP and gRPC services using a consistent, intuitive model, reducing the learning curve for developers.
- Simplified development: The framework includes helper methods that make service implementation significantly easier.
- Integrated deployment: You can host gRPC and HTTP endpoints on the same WebServer instance, even sharing the same port.

## Maven Coordinates

To enable gRPC Server, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-grpc</artifactId>
</dependency>
```

Additional dependencies may be required depending on your application needs. See the [gRPC SE Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/grpc) for a complete example.

## Usage

### gRPC Server Routing

- [Customizing Service Definitions](#customizing-service-definitions)

Unlike the HTTP server—which routes requests based on path expressions and HTTP verbs—the gRPC server routes requests by service and method names. This simplifies routing configuration: all you need to do is register your services.

``` java
private static GrpcRouting.Builder createRouting(Config config) {
    return GrpcRouting.builder()
            .service(new GreetService(config)) 
            .service(new EchoService())        
            .service(new MathService())        
            .unary(Strings.getDescriptor(),    
                   "StringService",
                   "Upper",
                   Main::grpcUpper);
}
```

- Register `GreetFeature` instance.
- Register `EchoService` instance.
- Register `MathService` instance.
- Register a custom unary gRPC route

Both standard gRPC services that implement the `io.grpc.BindableService` interface (typically created by extending generated server-side stubs and overriding their methods) and Helidon gRPC services that implement the io.helidon.grpc.server.GrpcService interface can be registered.

The key difference is that Helidon gRPC services provide finer-grained control—allowing you to customize behavior at the method level—and include several helper methods that simplify service implementation, as we’ll see shortly.

#### Customizing Service Definitions

When registering a service, regardless of its type, you can customize its descriptor by providing an instance of `ServerServiceDefinition` as an argument to the `service` method.

### Service Implementation

#### Implementing Protobuf Services

To implement Protobuf-based services, you can follow the official [instructions](https://grpc.io/docs/quickstart/java.html) on the gRPC website, which boil down to the following:

##### Define the Service IDL

For this example, we will re-implement the `EchoService` above as a Protobuf service in `echo.proto` file.

``` proto
syntax = "proto3";
option java_package = "org.example.services.echo";

service EchoService {
  rpc Echo (EchoRequest) returns (EchoResponse) {}
}

message EchoRequest {
  string message = 1;
}

message EchoResponse {
  string message = 1;
}
```

When using Maven, `.proto` files should be placed under the `src/main/proto` directory. It’s recommended to use the `protobuf-maven-plugin` to compile these files as part of the Maven build process. You can refer to the [pom.xml](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/grpc/pom.xml) file in the Helidon gRPC SE example for guidance.

The Protobuf compiler generates message classes (EchoRequest and EchoResponse), client stubs (for making RPC calls to the server), and a base class for the server-side service implementation. In this example, we’ll ignore the generated base class and instead implement the service using the Helidon gRPC framework.

##### Implement the Service

The service implementation will be very similar to our original implementation:

``` java
class EchoService implements GrpcService {
    @Override
    public Descriptors.FileDescriptor proto() {
        return Echo.getDescriptor(); 
    }

    @Override
    public void update(Routing routing) {
        routing.unary("Echo", this::echo); 
    }

    /**
     * Echo the message back to the caller.
     *
     * @param request  the echo request containing the message to echo
     * @param observer the response observer
     */
    public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {  
        String message = request.getMessage();  
        Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage(message).build();  
        complete(observer, response);  
    }
}
```

- Specify the proto descriptor in order to provide the necessary type information and enable Protobuf marshalling.
- Define the unary method `Echo` and map it to the `this::echo` handler.
- Create a handler for the `Echo` method, using Protobuf message types for request and response.
- Extract the message string from the request.
- Create the response containing the extracted message.
- Send the response back to the client by completing the response observer.

> [!NOTE]
> The `complete` method shown in the example above is just one of many helper methods available in the `ResponseHelper` class. See the full list [here](/apidocs/io.helidon.webserver.grpc/io/helidon/webserver/grpc/ResponseHelper.html).

### Server Interceptors

gRPC supports the concept of *server interceptors*, which are useful for implementing cross-cutting concerns across any subset of methods or services. Interceptors implement the `io.grpc.ServerInterceptor` interface, which defines a single `interceptCall` method. Server interceptors are arranged in a chain that wraps the actual gRPC method invocation and can be ordered by weight.

Server interceptors are registered during route creation and will intercept all subsequent gRPC method calls. Because of this, registration order is important to ensure that interceptor chains are constructed correctly to achieve the desired behavior. For example, consider the following routing definition:

``` java
GrpcRouting.builder()
        .service(new GreetService(config))
        .intercept(new Interceptor1())
        .service(new EchoService())
        .intercept(InterceptorWeights.USER + 100, new Interceptor2())
        .service(new MathService())
        .build();
```

This routing includes two server interceptor instances of types `Interceptor1` and `Interceptor2`. As stated above, the order in which these interceptors are registered is important. In this example, `Interceptor1` will be called for any method in `EchoService` and `MathService`, but not for `GreetService`. Similarly, `Interceptor2` will be called only for methods in `MathService`. The default weight of a server interceptor is `InterceptorWeights.USER`; it follows that for `MathService`, `Interceptor2` will be called *before* `Interceptor1` given its *higher* weight of `Interceptor.USER + 100`.

> [!NOTE]
> Even though the gRPC API supports interception of methods via alternative mechanisms, it is recommended to use the `intercept` method on a `GrpcRouting` builder, as shown above, to ensure correct ordering based on weights.

### Metrics

Helidon supports a few metrics that are specific to gRPC and are based on those defined in [gRPC OpenTelemetry Metrics](https://grpc.io/docs/guides/opentelemetry-metrics/). Metrics are disabled by default, but can be easily enabled via configuration as we shall discuss shortly.

Here is the list of gRPC server metrics available in Helidon:

| Metric | Type | Labels | Description |
|----|----|----|----|
| grpc.server.call.started | Counter | grpc.method | The total number of calls started, including not completed ones, for a certain method. |
| grpc.server.call.duration | Timer | grpc.method, grpc.status | Timer that tracks call durations for a certain method. |
| grpc.server.call.sent_total_compressed_message_size | Distribution Summary | grpc.method, grpc.status | Summary of message sizes sent to clients for a certain method. |
| grpc.server.call.rcvd_total_compressed_message_size | Distribution Summary | grpc.method, grpc.status | Summary of message sizes received from clients for a certain method. |

At the time of writing, Helidon only tracks successful method calls, so the value of the `grpc.status` label is always set to the string "OK". Support for metrics of unsuccessful calls may be added in the future, hence the need to include the label at this time.

As stated above, gRPC metrics are disabled by default but can be enabled by configuring the gRPC protocol in the Webserver. This can be accomplished either programmatically or directly in your server config file as follows:

``` yaml
server:
  port: 8080
  host: 0.0.0.0
  protocols:
    grpc:
      enable-metrics: true
```

The configuration above shall enable metrics on the Webserver’s default port 8080. For more information see [Helidon Metrics](../../se/metrics/metrics.md).

## Configuration

Configure the gRPC server using the Helidon configuration framework, either programmatically or via a configuration file.

### Configuring the gRPC Server

Currently, we do not have any custom configuration options for the gRPC protocol.

To register a routing with Helidon WebServer, simply add the routing to the listener (WebServer configuration is itself the default listener configuration)

``` java
WebServer.builder()
        .port(8080)
        .routing(httpRouting -> httpRouting.get("/greet", (req, res) -> res.send("Hi!"))) 
        .addRouting(GrpcRouting.builder()  
                            .unary(Strings.getDescriptor(),
                                   "StringService",
                                   "Upper",
                                   Main::grpcUpper))
        .build()
        .start();
```

- Configure HTTP routing of the server
- Configure gRPC routing of the server

### Configuring the gRPC Reflection Service

When a gRPC client interacts with a server, it must have access to the corresponding `.proto` file to understand the available services, methods, and message types. In many applications, this information is *common knowledge* shared between the client and server.

However, during development—especially when testing a new service—it can be useful to use tools such as `grpcurl` to invoke service methods directly. In such cases, one option is to provide the `.proto` file as a command-line argument, as shown below:

``` bash
>> grpcurl -proto strings.proto -d '{ "text": "hello world" }' localhost:8080 StringService.Split
```

The parameter `-proto` is used by `grpcurl` to learn about the methods and messages types available in the proto file, and ultimately execute the requested gRPC call.

Helidon includes a gRPC reflection service that can be queried by tools such as `grpcurl` to learn about the available services—​similar to OpenAPI for REST services. The reflection service is implemented as a *feature* and can be enabled programmatically when adding the feature, or via config as follows:

``` yaml
  features:
    grpc-reflection:
      enabled: true
```

The feature accepts a list of sockets, or if omitted as seen above, it would enable the feature on all sockets. For security reasons, the gRPC reflection service is *disabled by default*; if enabled, it is recommended to disable the feature for production to avoid any unwanted requests. For more information about gRPC reflection, see [gRPC Reflection](https://grpc.io/docs/guides/reflection/).

### Configuring Compression

gRPC compression is typically driven by client requests and can be asymmetric—that is, the server may use a different compression type than the client. In certain scenarios, such as debugging or performance testing, it may be useful to disable compression on the server side. As with most Helidon features, this can be configured either programmatically or through configuration.

``` yaml
server:
  port: 8080
  host: 0.0.0.0
  protocols:
    grpc:
      enable-compression: false
```

Compression is always *enabled* by default in Helidon, but can be disabled as shown above.

## Examples

The following gRPC examples for Helidon SE are available:

- [gRPC SE Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/grpc)
- [Multiple protocols on a single WebServer](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/protocols)
