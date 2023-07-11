WebClient
-----

Currently known clients in Helidon that we should consider:

The "HTTP" protocol
1. HTTP/1.1 client
2. HTTP/2 client
3. Eventually HTTP/3 client
4. grpc client (uses HTTP/2 as the base protocol, useless without HTTP/2)

Non-HTTP
1. WebSocket client (upgrades from HTTP/1.1, but fallback to HTTP protocol is useless)


When I, as a user want to access a service using its client, I start with the knowledge of what protocol I want to use:
- HTTP (any version)
- grpc
- WebSocket

This should bet determined by the API I choose as a starting point.
For HTTP, we should be able to provide order of preferences, depending on TLS support
Plaintext:
- HTTP/1
- HTTP/2 using upgrade
- HTTP/2 prior knowledge

TLS:
- ALPN (lists supported protocols, decides based on response)

```java
HttpClient http = HttpClient.builder()
    .versions(Http2Client.PROTOCOL) // ALPN (http,h2,h3)
    .addConfig(Http2ClientConfig.builder().usePriorKnowledge().build())
    .build();
```

Required features
1. allow `ClientResponse<T>` for cases where I do `request(T.class)`, that is not `AutoCloseable`
2. 

Features that should be common:

1. Proxy support (first do a `CONNECT`, then use the "inner" protocol over the tunneled stream)
2. ClientService support (for HTTP based protocols - not for WebSocket)
3. 