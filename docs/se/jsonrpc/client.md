# JSON-RPC Client

## Overview

The Helidon JSON-RPC client API is part of the WebClient API, and can be used to create [JSON-RPC 2.0](https://www.jsonrpc.org/specification) client applications. It offers built-in support to invoke JSON-RPC server methods with minimal effort, including handling of JSON parameters and processing of JSON responses.

## Maven Coordinates

To enable WebClient/JSON-RPC, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-jsonrpc</artifactId>
</dependency>
```

## Usage

### Simple Requests

An instance of `JsonRpcClient` can be obtained from a configured `WebClient` instance as shown next:

``` java
WebClient webClient = WebClient.builder()
        .baseUri("http://localhost:8080/rpc")
        .build();

JsonRpcClient client = webClient.client(JsonRpcClient.PROTOCOL);
```

The `WebClient` instance is configured with a base URI and the `JsonRpcClient` instance is created from it passing the `JsonRpcClient.PROTOCOL` selector.

To create a request, simply pass the method name, the ID and some parameters using the fluent API provided. Parameters must be JSON values, but simple Java types such as `String` and `int` are supported and mapped to the corresponding JSON types automatically.

``` java
JsonRpcClientResponse res = client.rpcMethod("start")
        .rpcId(1)
        .param("when", "NOW")
        .param("duration", "PT0S")
        .path("/machine")
        .submit();
```

A `JsonRpcClientResponse` is a subtype of `HttpClientResponse`, so any methods available in the latter apply to the former. Thus, we can easily verify the HTTP status and then inspect if any JSON-RPC result has been returned as follows:

``` java
if (res.status() == Status.OK_200 && res.result().isPresent()) {
    StartStopResult result = res.result().get().as(StartStopResult.class);
    if (result.status().equals("RUNNING")) {
        // success start!
    }
}
```

> [!NOTE]
> The HTTP status code is independent of any other error code in a JSON-RPC error response.

Every JSON-RPC response contains either a result or an error, and that is the reason why `res.result()` returns an optional value. The last step shows how the result is mapped to a `StartStopResult` instance using JSON-B. See [JSON-RPC Server](../../se/jsonrpc/server.md) for more information on these types.

### Batch Requests

The JSON-RPC client API also supports batching, whereby multiple method invocations can be aggregated and sent as a single unit for processing. The response to a batch request includes an entry for each of the invocations in the request; invocations are executed in order and can independently succeed or fail.

Here is an example that constructs a batch request to start and then stop a machine:

``` java
JsonRpcClientBatchRequest batch = client.batch("/machine");

batch.rpcMethod("start")
        .rpcId(1)
        .param("when", "NOW")
        .param("duration", "PT0S")
        .addToBatch()
        .rpcMethod("stop")
        .rpcId(2)
        .param("when", "NOW")
        .addToBatch();

JsonRpcClientBatchResponse batchRes = batch.submit();
```

The response of type `JsonClientBatchResponse` shall include an entry for each of the invocations in the request. In this example, we can test that the response returned HTTP status 200 and has a size of 2, and then verify the results by binding them to `StartStopResult` instances using JSON-B.

``` java
if (batchRes.status() == Status.OK_200 && batchRes.size() == 2) {
    Optional<JsonRpcResult> result0 = batchRes.get(0).result();
    if (result0.get().as(StartStopResult.class).status().equals("RUNNING")) {
        // successful start!
    }
    Optional<JsonRpcResult> result1 = batchRes.get(1).result();
    if (result0.get().as(StartStopResult.class).status().equals("STOPPED")) {
        // successful stop!
    }
}
```

As explained above, optional values are returned when trying to get a result since every individual batch response may include a result or an error.

## Configuration

At the time of writing, there is no configuration that is specific to the JSON-RPC client API other than what is already provided by the WebClient API itself. Note that the type `JsonRpcClientConfig` —that can be used to create a `JsonRpcClient` instance— extends `HttpClientConfig`, so HTTP configuration applies to JSON-RPC clients as well.

## Examples

The code snippets in this document are part of the JSON-RPC example available here:

- [JSON-RPC Machine Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/jsonrpc)
