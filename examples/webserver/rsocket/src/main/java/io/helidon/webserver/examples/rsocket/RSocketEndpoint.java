package io.helidon.webserver.examples.rsocket;

import io.netty.buffer.ByteBuf;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadata.Entry;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.transport.ServerTransport.ConnectionAcceptor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import reactor.core.publisher.Mono;

public class RSocketEndpoint extends Endpoint {

    final ConnectionAcceptor connectionAcceptor;
    final Map<String, HelidonDuplexConnection> connections = new ConcurrentHashMap<>();

    public RSocketEndpoint() {
        this.connectionAcceptor = RSocketServer
                .create()
                .acceptor(new SocketAcceptor() {

                    // TODO: Write down a rsocket client abstraction
                    //       rsocketHelidon.route("rsocket-endpoint")
                    @Override
                    public Mono<RSocket> accept(ConnectionSetupPayload connectionSetupPayload, RSocket rSocket) {

                        final String defaultMimeType = connectionSetupPayload.dataMimeType(); // octet / json / ...

                        Optional<String> connectionRouteOpt = extractRoute(connectionSetupPayload.metadata());
                        // http headers
                        return Mono.just(new RSocket() {
                            @Override
                            public Mono<Void> fireAndForget(Payload payload) {

                                Optional<String> connectionRouteOpt = extractRoute(payload.metadata());
                                //extractDataMimeType(payload.metadata());

                                return Mono.empty();
                            }

                            @Override
                            public Mono<Payload> requestResponse(Payload payload) {
                                return RSocket.super.requestResponse(payload);
                            }
                        });
                    }


                })
                .asConnectionAcceptor();
    }


    static class Handler {

        Pattern antPattern;
        boolean acceptMany;
        boolean returnMany;

    }

    // requestResponse
    // fireAndForget
    // requestStream
//    private Single<Void> handleSomething(String data) {
//
//    }
//
//  // requestResponse
//  // requestStream
//  private Single<String> handleSomething(String data) {
//
//  }
//
//  // requestStream
//  private Multi<String> handleSomething(String data) {
//
//  }
//
//  // requestChannel
//  // requestStream (Multi is always of a single element)
//  private Multi<String> handleSomething(Multi<String> datas) {
//
//  }
//
//  // requestChannel
//  private Single<String> handleSomething(Multi<String> datas) {
//
//  }
//
//  // requestChannel
//  private Single<Void> handleSomething(Multi<String> datas) {
//
//  }


    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        final HelidonDuplexConnection connection = new HelidonDuplexConnection(session);
        connections.put(session.getId(), connection);
        connectionAcceptor.apply(connection).subscribe();
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        connections.get(session.getId()).onCloseSink.tryEmitEmpty();
    }

    static Optional<String> extractRoute(ByteBuf metadata) {
        final CompositeMetadata compositeMetadata = new CompositeMetadata(metadata, false);

        for (Entry compositeMetadatum : compositeMetadata) {
            final String key = compositeMetadatum.getMimeType();
            final ByteBuf payload = compositeMetadatum.getContent();


            if (WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString().equals(key)) {
                final RoutingMetadata routes = new RoutingMetadata(payload);

                for (String route : routes) {
                    //registry.findRoute(route);
                    return Optional.of(route);
                }
            }

            return Optional.empty();
        }
        return Optional.empty();
    }
}