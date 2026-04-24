# JSON-RPC Server

## Overview

The Helidon WebServer provides a framework for creating [JSON-RPC 2.0](https://www.jsonrpc.org/specification) applications. The JSON-RPC protocol is a stateless and lightweight protocol based on JSON that runs on top of HTTP/1.1. It offers the ability to invoke remote methods passing parameters and getting results as JSON values.

## Maven Coordinates

To enable WebServer/JSON-RPC, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-jsonrpc</artifactId>
</dependency>
```

## Usage

### Service Implementation

JSON-RPC routing is multi-leveled: first-level routing is similar to HTTP using path expressions, and second-level routing is based on method names in JSON payloads. After routing, a JSON-RPC method handler is invoked and given access to any parameters with the option of responding either with a result or an error.

Setting the Helidon WebServer to accept JSON-RPC requests starts by building a `JsonRpcRouting` instance that includes individual method routes or a group of routes aggregated by a *service*.

```java
JsonRpcRouting jsonRpcRouting = JsonRpcRouting.builder()
        .service(new MachineService())
        .build();

WebServer.builder()
        .port(8080)
        .host("localhost")
        .routing(r -> r.register("/rpc", jsonRpcRouting))
        .build()
        .start();
```

In the example above, the `JsonRpcRouting` instance is created from a single JSON-RPC service `MachineService` and registered in the WebServer under the `/rpc` path. The `MachineService` class must extend `JsonRpcService` and override the `routing(JsonRpcRules)` method to add mappings for each of the JSON-RPC method names supported by the application. This is very similar to the way an `HttpService` is defined except for the multi-leveled mapping that includes paths and JSON-RPC method names as shown next.

```java
class MachineService implements JsonRpcService {

    @Override
    public void routing(JsonRpcRules rules) {
        rules.register("/machine",
                       JsonRpcHandlers.builder()
                               .method("start", this::start)
                               .method("stop", this::stop)
                               .build());
    }

    void start(JsonRpcRequest req, JsonRpcResponse res) {
        StartStopParams params = req.params().as(StartStopParams.class);
        if (params.when().equals("NOW")) {
            res.result(new StartStopResult("RUNNING"));
        } else {
            res.error(JsonRpcError.INVALID_PARAMS, "Bad param");
        }
        res.send();
    }

    void stop(JsonRpcRequest req, JsonRpcResponse res) {
        StartStopParams params = req.params().as(StartStopParams.class);
        if (params.when().equals("NOW")) {
            res.result(new StartStopResult("STOPPED"));
        } else {
            res.error(JsonRpcError.INVALID_PARAMS, "Bad param");
        }
        res.send();
    }
}
```

This JSON-RPC service registers handlers for method names `start` and `stop` under the path `/machine`, thus JSON-RPC clients shall use the `/rpc/machine` URI to send requests —see `JsonRpcRouting` instance creation above.

The logic for the two methods `start` and `stop` is very similar. First, they inspect parameters, then they decide to return either a result or an error, and finally they call `send()` on the response. Parameters, as well as results, can be either JSON-P instances or JSON-B objects. In this example, we defined some simple records to bind and serialize data using JSON-B.

```java
public record StartStopParams(String when, Duration duration) {
}

public record StartStopResult(String status) {
}
```

> [!NOTE]
> These record types used during serialization must be public for the JSON-B implementation (Eclipse Yasson in our example) to have access to them.

## Configuration

At the time of writing, there is no configuration that is specific to the JSON-RPC feature other than what is already provided by the WebServer itself.

## Examples

The code snippets in this document are part of the JSON-RPC example available here:

- [JSON-RPC Machine Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/webserver/jsonrpc)
