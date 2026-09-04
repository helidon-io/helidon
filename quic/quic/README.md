# Helidon QUIC

The `helidon-quic` module provides an incubating, standalone QUIC client, server, session, and stream API. It can be
used to implement application protocols other than HTTP/3 without depending on the WebServer or WebClient HTTP/3
integration.

## Maven Coordinate

```xml
<dependency>
    <groupId>io.helidon.quic</groupId>
    <artifactId>helidon-quic</artifactId>
</dependency>
```

## Programming Model

`QuicClient.connect`, `QuicServer.accept`, stream-open, stream-read, and stream-write operations block and are intended
to run on virtual threads. The default transport executor is a virtual-thread-per-task executor. A configured executor
is borrowed: the client and server never close it.

Both `connect` and `accept` return a handshake-complete `QuicSession`. The session's
`applicationProtocol()` is the protocol selected through ALPN.

Choose a protocol-specific ALPN identifier and configure it on both endpoints:

```java
String alpn = "example-protocol/1";

QuicServer server = QuicServer.builder()
        .tls(serverTls)
        .applicationProtocols(List.of(alpn))
        .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        .build();

QuicClient client = QuicClient.builder()
        .tls(clientTls)
        .build();
```

The server TLS configuration must provide its private key and certificate chain. The client TLS configuration must
trust the server certificate, for example using a configured trust store:

```java
Keys serverKeys = Keys.builder()
        .keystore(store -> store
                .passphrase("changeit")
                .keyAlias("server")
                .certChainAlias("server")
                .keystore(Resource.create("server-keystore.p12")))
        .build();

Tls serverTls = Tls.builder()
        .privateKey(serverKeys.privateKey().orElseThrow())
        .privateKeyCertChain(serverKeys.certChain())
        .build();

Tls clientTls = Tls.builder()
        .trust(trust -> trust.keystore(store -> store
                .passphrase("changeit")
                .trustStore(true)
                .keystore(Resource.create("client-truststore.p12"))))
        .build();
```

`QuicClientTarget` separates the resolved UDP address from the TLS identity. The peer address must be resolved and have
a positive port. Use the DNS name covered by the server certificate as `tlsPeerName`; it is used for certificate
validation and, for DNS names, SNI. The TLS peer port defaults to the UDP peer port.

```java
server.start();
FutureTask<QuicSession> acceptedSession = new FutureTask<>(server::accept);
Thread.ofVirtual().start(acceptedSession);

InetSocketAddress peerAddress = server.localAddress();
QuicClientTarget target = QuicClientTarget.builder()
        .peerAddress(peerAddress)
        .tlsPeerName("server.example.com")
        .applicationProtocols(List.of(alpn))
        .buildPrototype();

QuicSession clientSession = client.connect(target);
QuicSession serverSession = acceptedSession.get();
```

The ordered ALPN lists must be non-empty and contain no duplicates. Each Java character from U+0000 through U+00FF maps
one-to-one to an opaque ALPN byte using ISO-8859-1; characters outside that byte range are rejected. Check
`session.applicationProtocol()` before dispatching to application-protocol code.

## Sessions and Streams

A session can open locally initiated bidirectional and unidirectional streams. Opening a stream can block while waiting
for peer stream credit and uses the configured `streamOpenTimeout` by default. `acceptStream()` blocks until the next
remotely initiated stream; only one `acceptStream()` call may be outstanding for a session.

```java
QuicBidirectionalStream stream = clientSession.openBidirectionalStream();
stream.writeFinal(BufferData.create(payload));

for (Optional<BufferData> next = stream.read();
        next.isPresent();
        next = stream.read()) {
    consume(next.orElseThrow());
}
```

An empty `read()` result is FIN. `write` and `writeFinal` synchronously consume and copy all unread bytes from the
provided buffer, then wait until the data reaches the QUIC packet path; they do not wait for peer acknowledgement.
Use `finish()` for FIN without more data, `reset(errorCode)` to abort a sending side, and
`stopReading(errorCode)` to ask the peer to stop sending.

The `read`, `write`, `writeFinal`, and `finish` operations throw `QuicStreamTerminationException` when one direction has
reached a terminal condition. Its `kind()` distinguishes ordinary closure (`CLOSED`), a local reset
(`RESET_LOCALLY`), a peer reset (`RESET_BY_PEER`), and a peer `STOP_SENDING` request (`STOP_SENDING`).
`applicationErrorCode()` contains the application protocol error code for either reset and for `STOP_SENDING`; it is
empty for ordinary closure. Application error codes passed to `reset` and `stopReading` must be between `0` and
2<sup>62</sup> - 1, inclusive. Both methods are no-ops after their respective direction is terminal.
`stopSendingReceived()` is a non-throwing current-state accessor. `whenStopSendingReceived()` normally completes with
the peer's application error code; if the connection terminates first, the stage completes exceptionally with
`QuicException`.

## Ownership and Shutdown

The creating `QuicClient` or `QuicServer` owns the UDP transport, timers, TLS state, connection registry, and transport
caches. A `QuicSession` owns only application use of one connection.

Close each session with an application-protocol error code when application use finishes:

```java
clientSession.close(0);
serverSession.close(0);
```

The overload accepting peer detail sends that detail to the peer. Pass only bounded, sanitized text that contains no
secrets or untrusted exception messages. `whenTerminated()` completes after connection cleanup.

Use try-with-resources for clients and servers. `QuicClient.close()` is idempotent and closes its connections and owned
transport resources. `QuicServer.close()` calls `stop()` with the configured `shutdownTimeout`. A duration passed to
`stop(Duration)` must be non-null, strictly positive, and fit in signed-long nanoseconds; validation occurs before any
server lifecycle state change. A null duration throws `NullPointerException`; a zero, negative, or too-large duration
throws `IllegalArgumentException`. The method waits for graceful cleanup and throws `QuicException` if cleanup does not
complete before the timeout. Closing either endpoint does not close a configured executor.

## Current Limitations

- The standalone API is incubating and can change incompatibly.
- QUIC 0-RTT early data is not supported.
- Active connection migration is not supported; endpoints advertise `disable_active_migration`.
- The public application API exposes reliable bidirectional and unidirectional streams, not QUIC DATAGRAM frames.
- Application framing, message semantics, and application error-code allocation belong to the application protocol.
  Types marked `Api.Internal` are implementation details and are not part of the standalone application API.
