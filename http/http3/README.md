# HTTP/3 Core

This module contains the shared internal HTTP/3 protocol implementation used by Helidon's preview HTTP/3 client and
server integrations. Its exported Java types are marked `Api.Internal`; they are not a standalone public HTTP/3 API.
The protocol-neutral standalone APIs in `quic/quic` are incubating.

Module ownership is split as follows:

1. `http/http3` contains internal shared HTTP/3 framing, protocol helpers, and QPACK support.
2. `webclient/http3` contains the preview HTTP/3 client.
3. `webserver/http3` contains the preview HTTP/3 server and its integration tests.
4. `quic/quic` contains the QUIC transport and its incubating standalone client, server, session, and stream API.

The HTTP/3 integrations use the [Helidon-owned QUIC TLS handshake](../../quic/quic/README.md#tls-handshake), including its
ephemeral-key lifecycle and QUIC-specific packet-protection and alert rules.

The old split `quic/http3/{common,client,server}` reactor has been removed so the filesystem layout matches the new
module boundaries.
