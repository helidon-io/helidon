
# Helidon MP gRPC Server with Metrics and Tracing Example

This examples shows a simple gRPC application written using Helidon MP that enables
metrics and tracing.

This example can be run together with the [Basic gRPC Client example](../basic-client/README.md) 
which provides a microprofile gRPC client that uses the services deployed in this server.

## Build

```
mvn package
```

## Run

```
mvn exec:exec
```

Then the services can be accessed on the gRPC endpoint `localhost:1408` When the server is running
metrics can be accessed on `http://127.0.0.1:8080/metrics`

The service metrics can be seen to change each time that the [client](../basic-client/README.md) is run. 
