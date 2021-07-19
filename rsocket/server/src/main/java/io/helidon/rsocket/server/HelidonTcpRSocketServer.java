package io.helidon.rsocket.server;

import io.rsocket.core.RSocketServer;
import reactor.core.publisher.Mono;
import io.rsocket.transport.netty.server.TcpServerTransport;

import java.util.logging.Logger;

/**
 * Experimental RSocket Server for TCP connection.
 */
public class HelidonTcpRSocketServer {

    private final static Logger LOGGER = Logger.getLogger(HelidonTcpRSocketServer.class.getName());

    private final int port;
    private final RoutedRSocket rsocket;

    public void start(){
        RSocketServer.create()
                .acceptor((payload, rsocket) -> Mono.just(this.rsocket))
                .bindNow(TcpServerTransport.create(this.port));
        LOGGER.info("Starting RSocket Server on TCP port " + port);
    }

    public static Builder builder() {
        return new Builder();
    }

    private HelidonTcpRSocketServer(int port, RoutedRSocket rsocket) {
        this.port = port;
        this.rsocket = rsocket;
    }

    public static final class Builder {
        private int port = 9090;
        private RoutedRSocket rsocket;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder rsocket(RoutedRSocket rsocket) {
            this.rsocket = rsocket;
            return this;
        }

        public HelidonTcpRSocketServer build() {
            return new HelidonTcpRSocketServer(port,rsocket);
        }

    }

}
