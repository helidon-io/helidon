# gRPC Client

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
  - [Generated Stubs](#generated-stubs)
  - [Service Descriptors](#service-descriptors)
  - [Client URI Suppliers](#client-uri-suppliers)
  - [Client Interceptors](#client-interceptors)
  - [Metrics](#metrics)
  - [Tracing](#tracing)
- [Configuration](#configuration)

## Overview

The Helidon gRPC client API is part of the WebClient API, but with specific support to invoke remote procedures and to register handlers for responses. All four types of gRPC calls are supported: unary, bi-directional, client stream and server stream. A Helidon gRPC client can be configured either using generated stubs (the most popular option) or using manually crafted service descriptors.

## Maven Coordinates

To enable gRPC Client, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-grpc</artifactId>
</dependency>
```

## Usage

### Generated Stubs

A Helidon gRPC client can be configured from generated protobuf stubs. In what follows, we shall use the following proto file and the corresponding stubs generated using the `protoc` command:

``` proto
syntax = "proto3";
option java_package = "my.package";

service StringService {
  rpc Upper (StringMessage) returns (StringMessage) {}
  rpc Split (StringMessage) returns (stream StringMessage) {}
}

message StringMessage {
  string text = 1;
}
```

The gRPC protocol runs on top of HTTP/2, and as such requires TLS configuration to establish a connection. Thus, the first step is to configure TLS as shown next:

``` java
Tls clientTls = Tls.builder()
        .trust(trust -> trust
                .keystore(store -> store
                        .passphrase("password")
                        .trustStore(true)
                        .keystore(Resource.create("client.p12"))))
        .build();
```

After creating a `Tls` instance, a `WebClient` can be created as follows:

``` java
WebClient webClient = WebClient.builder()
        .tls(clientTls)
        .baseUri("https://localhost:8080")
        .build();
```

So far, this is all the same as for accessing any protected REST endpoint; the next step is to obtain a gRPC client stub using our newly created client. This can be accomplished by *switching* the client protocol to gRPC, and using its channel to create a stub:

``` java
GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
StringServiceGrpc.StringServiceBlockingStub service =
        StringServiceGrpc.newBlockingStub(grpcClient.channel());
```

Once a stub is created, it can be used to invoke any of its declared methods, such as `upper` to uppercase a string:

``` java
Strings.StringMessage msg1 = newMessage("hello");
Strings.StringMessage res1 = service.upper(msg1);
String uppercased = res1.getText();
```

When it comes to invoking a method that can return more than one value, there are two options: it can block (we are using virtual theads after all!) and return back an `Iterator` or you can provide a `StreamObserver` as it is more commonly done when using gRPC. Let’s consider the case of the `split` method that breaks up a sentence into individual words, and can thus return multiple string messages.

Using an iterator as a result:

``` java
Strings.StringMessage msg2 = newMessage("hello world");
Iterator<Strings.StringMessage> res2 = service.split(msg2);
while (res2.hasNext()) {
    // ...
}
```

Passing a stream observer and collecting all the messages into a `Future` that returns an iterator:

``` java
Strings.StringMessage msg3 = newMessage("hello world");
CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
service.split(msg3, new StreamObserver<Strings.StringMessage>() {
    private final List<Strings.StringMessage> value = new ArrayList<>();

    @Override
    public void onNext(Strings.StringMessage value) {
        this.value.add(value);
    }

    @Override
    public void onError(Throwable t) {
        future.completeExceptionally(t);
    }

    @Override
    public void onCompleted() {
        future.complete(value.iterator());
    }
});
```

### Service Descriptors

Service descriptors are an alternative to using generated stubs and the `protoc` compiler. A service descriptor provides service meta-data to the WebClient for the purpose of carrying out invocations. The descriptor includes, the service name, and a description of each service method, including its type, what it accepts and what it returns.

The following is a descriptor for a service that includes the methods called in the previous section using a stub:

``` java
GrpcServiceDescriptor serviceDescriptor = GrpcServiceDescriptor.builder()
        .serviceName("StringService")
        .putMethod("Upper",
                   GrpcClientMethodDescriptor.unary("StringService", "Upper")
                           .requestType(Strings.StringMessage.class)
                           .responseType(Strings.StringMessage.class)
                           .build())
        .putMethod("Split",
                   GrpcClientMethodDescriptor.serverStreaming("StringService", "Split")
                           .requestType(Strings.StringMessage.class)
                           .responseType(Strings.StringMessage.class)
                           .build())
        .build();
```

Configuring a `WebClient` with `Tls` is done in the same manner as shown above for the stub case. Once the gRPC client is created, a service descriptor can be provided, and a method invoked using the methods `unary`, `clientStream`, `serverStream` or `bidi`. For example,

``` java
Strings.StringMessage res = grpcClient.serviceClient(serviceDescriptor)
        .unary("Upper", newMessage("hello"));
```

### Client URI Suppliers

A `ClientURISupplier` can be used to dynamically obtain a sequence of `ClientUri` instances to access when executing a gRPC request. If a client URI supplier is configured, the Helidon gRPC implementation will attempt to connect to each endpoint one by one, in the order provided, until a connection is successfully established. This feature is useful in certain environments in which more than one identical server is available, but with some potentially unavailable or unreachable.

A few common implementations are provided in `ClientUriSuppliers`. These include suppliers for strategies such as random, round-robin, among others. Applications can either use one of the built-in suppliers or create their own.

The following example configures a round-robin supplier using a collection of known servers:

``` java
GrpcClient grpcClient = GrpcClient.builder()
        .tls(clientTls)
        .clientUriSupplier(RoundRobinSupplier.create(myServers()))
        .build();
```

If both a base URI and a client URI supplier are configured, the latter will take precendence over the former.

### Client Interceptors

The gRPC API supports the notion of an interceptor on a channel. Interceptors are useful to implement cross-cutting concerns that apply to many or all invocations. These may include security, logging, metrics, etc. They can be specified directly on the channel returned by a `GrpcClient`, effectively *wrapping* that channel with a list of interceptors to execute on every invocation.

``` java
Channel newChannel = grpcClient.channel(myInterceptors());
```

## Metrics

Helidon supports a few metrics that are specific to gRPC clients and are based on those defined in [gRPC OpenTelemetry Metrics](https://grpc.io/docs/guides/opentelemetry-metrics/). Metrics are disabled by default, but can be easily enabled as we shall discuss shortly.

Here is the list of gRPC client metrics available in Helidon:

| Metric | Type | Labels | Description |
|----|----|----|----|
| grpc.client.attempt.started | Counter | grpc.method, grpc.target | The total number of calls started, including not completed ones, for a certain method and target. |
| grpc.client.attempt.duration | Timer | grpc.method, grpc.target, grpc.status | Timer that tracks call durations for a certain method and target. |
| grpc.client.attempt.sent_total_compressed_message_size | Distribution Summary | grpc.method, grpc.target, grpc.status | Summary of message sizes sent to clients for a certain method and target. |
| grpc.client.attempt.rcvd_total_compressed_message_size | Distribution Summary | grpc.method, grpc.target, grpc.status | Summary of message sizes received from clients for a certain method and target. |

The value of the label `grpc.method` is the fully-qualified method name; the value of the label `grpc.target` is the base URI of the gRPC service. At the time of writing, Helidon only tracks *successful* client method calls, so the value of the `grpc.status` label is always set to the string "OK". Support for metrics of unsuccessful calls may be added in the future, hence the need to include the label at this time.

As stated above, gRPC client metrics are disabled by default but can be enabled programmatically when building the client as shown next:

``` java
GrpcClient grpcClient = GrpcClient.builder()
        .tls(clientTls)
        .baseUri("https://localhost:8080")
        .enableMetrics(true)        // enables metrics
        .build();
```

> [!NOTE]
> Metrics are only available for gRPC clients running in a server environment.

For more information see [Helidon Metrics](../../se/metrics/metrics.md).

## Tracing

Tracing in the gRPC client is implemented as a so-called gRPC client service. To enable tracing support, you need to list tracing as an available service either programmatically or via config, and include the following dependency in your project:

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-grpc-tracing</artifactId>
</dependency>
```

Tracing support is loaded via the gRPC client service SPI and made available to clients. Using config, we can list it as an available gRPC service as follows:

``` yaml
grpc-client:
  grpc-services:
    tracing:
```

At the time of writing, no additional configuration is necessary under the `tracing:` section. Finally, a gRPC client instance can be created that is configured with tracing support enabled as follows:

``` java
Config config = Config.create().get("grpc-client");
GrpcClient grpcClient = GrpcClient.builder()
        .config(config)     // with tracing
        .tls(clientTls)
        .baseUri("https://localhost:8080")
        .build();
```

## Configuration

TLS can be configured externally, just like it is done when using the WebClient to access an HTTP endpoint. For more information, see [Configuring the WebClient](https://helidon.io/docs/v4/se/webclient#_configuring_the_webclient).

There are a few configuration options (see table below) that are specific to a `GrpcClient` and can be configured using a `GrpcClientProtocolConfig` instance. See [RandomServiceTest](https://github.com/helidon-io/helidon-examples/blob/dev-4.x/examples/webserver/grpc-random/src/test/java/io/helidon/examples/webserver/grpc/random/RandomServiceTest.java) for an example.

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a76ff7-abort-poll-time-expired"></span> `abort-poll-time-expired` | `VALUE` | `Boolean` | `false` | Whether to continue retrying after a poll wait timeout expired or not |
| <span id="a6ed8e-heartbeat-period"></span> `heartbeat-period` | `VALUE` | `Duration` | `PT0S` | How often to send a heartbeat (HTTP/2 ping) to check if the connection is still alive |
| <span id="a2b249-init-buffer-size"></span> `init-buffer-size` | `VALUE` | `Integer` | `2048` | Initial buffer size used to serialize gRPC request payloads |
| <span id="ac8065-name"></span> `name` | `VALUE` | `String` | `grpc` | Name identifying this client protocol |
| <span id="a133d3-next-request-wait-time"></span> `next-request-wait-time` | `VALUE` | `Duration` | `PT1S` | When data has been received from the server but not yet requested by the client (i.e., listener), the implementation will wait for this duration before signaling an error |
| <span id="ad5f5d-poll-wait-time"></span> `poll-wait-time` | `VALUE` | `Duration` | `PT10S` | How long to wait for the next HTTP/2 data frame to arrive in underlying stream |
