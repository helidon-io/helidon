# RSocket integration proposal

## About RSocket

RSocket is an application protocol initially developed by Netflix that supports Reactive Streams. The motivation behind its development was to replace hypertext transfer protocol (HTTP), which is inefficient for many tasks such as microservices communication, with a protocol that has less overhead.

RSocket is a binary protocol for use on byte stream transports such as TCP, WebSockets, and Aeron. It enables the following symmetric interaction models via async message passing over a single connection:

* request/response (stream of 1)
* request/stream (finite stream of many)
* fire-and-forget (no response)
* channel (bi-directional streams)

It supports session resumption, to allow resuming long-lived streams across different transport connections. This is particularly useful for mobile‹–›server communication when network connections drop, switch, and reconnect frequently.

## Proposal

Support of RSocket is a good fit for Helidon, along with gRPC, WebSocket etc. 
Currently RSocket is mostly Spring/Reactor oriented.

Current support is PoC, and subject of change since the major `Routing` behaviour in Helidon may change in version 3.0.

The current proposal intends to implement:

1. SE and MP API similar to gRPC, but with the respect to RSocket particular features;
2. At the current stage, reuse Tyrus module, since RSocket is quite similar;
3. Implement our custom routing in RSocket to fit Helidon API;
4. Examples should be provided;
5. RSocket configuration should be done via Helidon Config.
6. All Reactor API should be hidden or replaced from Helidon APIs





