package io.helidon.rsocket.metrics;

import io.helidon.metrics.RegistryFactory;
import io.helidon.rsocket.server.HelidonDuplexConnection;
import io.helidon.rsocket.server.RSocketEndpoint;
import io.helidon.rsocket.server.RSocketRouting;
import io.helidon.rsocket.server.RoutedRSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.transport.ServerTransport;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

public class MeteredRSocketEndpoint extends RSocketEndpoint {

    protected final Map<String, MetricsDuplexConnection> connections = new ConcurrentHashMap<>();

    public MeteredRSocketEndpoint(){
        //Empty constructor required for Tyrus
    }

    public static MeteredRSocketEndpoint create(RSocketRouting routing, String path){
        return new MeteredRSocketEndpoint(routing,path);
    }

    public MeteredRSocketEndpoint(RSocketRouting routing, String path) {
        this.path = path;

        RoutedRSocket routedRSocket = RoutedRSocket.builder()
                .fireAndForgetRoutes(routing.fireAndForgetRoutes())
                .requestChannelRoutes(routing.requestChannelRoutes())
                .requestResponseRoutes(routing.requestResponseRoutes())
                .requestStreamRoutes(routing.requestStreamRoutes())
                .build();
        MetricRegistry registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

        MetricsRSocket metricsRSocket = new MetricsRSocket(routedRSocket,registry);

        ServerTransport.ConnectionAcceptor connectionAcceptor = RSocketServer
                .create()
                .acceptor((connectionSetupPayload, rSocket) -> Mono.just(metricsRSocket))
                .asConnectionAcceptor();

        connectionAcceptorMap.put(path,connectionAcceptor);
    }

    /**
     * Returns the created and configured RSocket Endpoint.
     */
    @Override
    public ServerEndpointConfig getEndPoint() {
        return ServerEndpointConfig.Builder.create(this.getClass(), path)
                .build();
    }

    /**
     * Function called on connection open, used to organize sessions.
     * @param session
     * @param endpointConfig
     */
    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        MetricRegistry registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

        final HelidonDuplexConnection connection = new HelidonDuplexConnection(session);
        final MetricsDuplexConnection metricsConnection = new MetricsDuplexConnection(DuplexConnectionInterceptor.Type.SERVER,connection,registry);

        connections.put(session.getId(), metricsConnection);
        connectionAcceptorMap.get(session.getRequestURI().getPath()).apply(metricsConnection).subscribe();
        connection.onClose().doFinally(con -> connections.remove(session.getId())).subscribe();
    }

    /**
     * Function called on connection close, used to dispose resources.
     *
     * @param session
     * @param closeReason
     */
    @Override
    public void onClose(Session session, CloseReason closeReason) {
        connections.get(session.getId()).dispose();
    }

    /**
     * Function called on Error received, cleans up the resources.
     * @param session
     * @param thr
     */
    @Override
    public void onError(Session session, Throwable thr) {
        connections.get(session.getId()).dispose();
    }
}
