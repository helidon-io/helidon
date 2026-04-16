# gRPC MP Client

## Overview

Building Java-based gRPC clients using the Helidon MP gRPC API is very simple and removes a lot of the boilerplate code typically associated with more traditional approaches of writing gRPC clients. At its simplest, a gRPC Java client can be written using nothing more than a suitably annotated Java interface.

## Maven Coordinates

To enable gRPC MicroProfile Clients, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile.grpc</groupId>
    <artifactId>helidon-microprofile-grpc-client</artifactId>
</dependency>
```

## API

The following annotations are used to work with Helidon MP gRPC clients:

- `@Grpc.GrpcChannel` - an annotation used to inject a gRPC channel.
- `@Grpc.GrpcProxy` - an annotation used to mark an injection point for a gRPC service client proxy.
- `@Grpc.GrpcService` - an annotation used to specify the name of a gRPC service to connect to.

## Configuration

For a gRPC client to connect to a server, it requires a channel. Channels are configured in the `grpc` section of the Helidon application configuration. The examples below use an `application.yaml` file but there are many other ways to to configure Helidon. See [Configuration in Helidon](../../mp/config/introduction.md) for more information.

``` yaml
grpc:
  client:
    channels:  
      - name: "string-channel"  
        host: localhost  
        port: 8080  
```

- Channels are configured in the `channels` section under `grpc.client`.
- The name of the channel as referred to in the application code.
- The host name for the channel (defaults to localhost).
- The port number for the channel (defaults to 1408).

While most client applications only connect to a single server, it is possible to configure multiple (an array of) named channels if the client needs to connect to multiple servers.

### Configuring TLS

gRPC runs on top of HTTP/2 which prefers secure TLS connections. Most gRPC channels will also include a section to configure TLS. Here is a sample of that configuration for the `string-channel`:

``` yaml
grpc:
  client:
    channels:
      - name: "string-channel"
        port: 8080
        tls:
          trust:
            keystore:
              passphrase: "password"
              trust-store: true
              resource:
                resource-path: "client.p12"
          private-key:
            keystore:
              passphrase: "password"
              resource:
                resource-path: "client.p12"
```

TLS in the gRPC MP client section is configured in the same way as in other Helidon components such as the webserver. For more information see [Configuring TLS](../../se/webserver/webserver.md#_configuring_tls).

Given that TLS is enabled by default in gRPC, it must be explicitly turned off by setting the `enabled` flag to `false` when connecting to an unsecure endpoint. For example, to turn off TLS for the `string-channel` above use:

``` yaml
grpc:
  client:
    channels:
      - name: "string-channel"
        port: 8080
        tls:
          enabled: "false"
```

> [!NOTE]
> It is not sufficient to omit the TLS section in the configuration above. The TLS section must be present and explicitly disabled. It is generally discouraged to expose unsecure gRPC endpoints.

## Usage

### Defining a Client Interface

The next step is to produce an interface with the service methods that the client requires. For example, suppose we have a simple service that has a unary method to convert a string to uppercase. To write a client for this service, all that is required is an interface as shown next:

``` java
@ApplicationScoped
@Grpc.GrpcService("StringService")  
@Grpc.GrpcChannel("string-channel")  
interface StringServiceClient {

    @Grpc.Unary
    String upper(String s);
}
```

- The `@Grpc.GrpcService` annotation is necessary to provide the name of the gRPC service when it differs from the interface name, as it is the case in this example.
- The `@Grpc.GrpcChannel` annotation is the qualifier that supplies the channel name. This is the same name as used in the channel configuration in the examples provided in the [Configuration section](#configuration).

There is no need to write any code to implement the client. The Helidon MP gRPC API will create a dynamic proxy for the interface using the information from the annotations and method signatures.

The interface in the example above uses the same method signature as the server, but this does not need to be the case. For example, it can use a `StreamObserver<String>` as a second parameter to return the result:

``` java
@ApplicationScoped
@Grpc.GrpcService("StringService")
@Grpc.GrpcChannel("string-channel")
interface StringServiceClient {

    @Grpc.Unary
    void upper(String s, StreamObserver<String> response);
}
```

### Injecting Client Proxies

Now that there is a client interface and a channel configuration, we can then use these in the client application. We can declare a field of the same type as the client service interface in the application class that requires the client. The field is then annotated so that CDI will inject the client proxy into the field.

``` java
@ApplicationScoped
public class MyAppBean {

    @Inject  
    @Grpc.GrpcProxy  
    private StringServiceClient stringServiceClient;
}
```

- The `@Inject` annotation tells CDI to inject the client implementation.
- The `@Grpc.GrpcProxy` annotation is used by the CDI container to match the injection point to the gRPC MP API provider.

When the CDI container instantiates `MyAppBean`, it will inject a dynamic proxy into the `stringServiceClient` field, and then provide the necessary logic for the proxy methods to convert a method call into a gRPC call.

In the example above, there is no need to use a channel directly. The correct channel is added to the dynamic client proxy internally by the Helidon MP gRPC API.

### Injecting Channels

Channels can also be directly injected into application bean instances. The Helidon gRPC client API has CDI producers to inject `io.grpc.Channel` instances.

For example, a class might have an injectable `io.grpc.Channel` field as follows:

``` java
    @Inject  
    @Grpc.GrpcChannel("string-channel")  
    private Channel channel;
```

- The `@Inject` annotation tells CDI to inject the channel.
- The `@Grpc.GrpcChannel` annotation supplies the channel name. This is the same name as used in the channel configuration in the examples provided in the [Configuration section](#configuration).

An injected channel can be used, for example, when directly instantiating `protoc` generated stubs.

## Examples

Please refer to the [Helidon gRPC MP Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/grpc).
