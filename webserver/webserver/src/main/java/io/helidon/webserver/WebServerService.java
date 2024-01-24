package io.helidon.webserver;

import java.util.List;
import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.media.MediaSupport;
import io.helidon.inject.service.Injection;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerFeature;

@Injection.Singleton
class WebServerService {
    private final Optional<Config> config;
    private final List<ServerFeature> serverFeatures;
    private final List<HttpFeature> httpFeatures;
    private final List<ProtocolConfig> protocolConfigs;
    private final List<ContentEncoding> contentEncodings;
    private final List<MediaSupport> mediaSupports;
    private final List<ServerConnectionSelector> connectionSelectors;
    private final List<DirectHandlers> directHandlers;
    private final Optional<RequestedUriDiscoveryContext> requestedUriDiscoveryContext;

    @Injection.Inject
    WebServerService(Optional<Config> config,
                     List<ServerFeature> serverFeatures,
                     List<HttpFeature> httpFeatures,
                     List<ProtocolConfig> protocolConfigs,
                     List<ContentEncoding> contentEncodings,
                     List<MediaSupport> mediaSupports,
                     List<ServerConnectionSelector> connectionSelectors,
                     List<DirectHandlers> directHandlers,
                     Optional<RequestedUriDiscoveryContext> requestedUriDiscoveryContext) {

        this.config = config;
        this.serverFeatures = serverFeatures;
        this.httpFeatures = httpFeatures;
        this.protocolConfigs = protocolConfigs;
        this.contentEncodings = contentEncodings;
        this.mediaSupports = mediaSupports;
        this.connectionSelectors = connectionSelectors;
        this.directHandlers = directHandlers;
        this.requestedUriDiscoveryContext = requestedUriDiscoveryContext;
    }

    void updateServerBuilder(WebServerConfig.Builder builder) {
        if (builder.config().isEmpty()) {
            config.map(it -> it.get("server")).ifPresent(builder::config);
        }
        if (builder.requestedUriDiscoveryContext().isEmpty()) {
            requestedUriDiscoveryContext.ifPresent(builder::requestedUriDiscoveryContext);
        }
        serverFeatures.forEach(builder::addFeature);
        for (HttpFeature httpFeature : httpFeatures) {
            String socket = httpFeature.socket();
            boolean required = httpFeature.socketRequired();
            boolean useDefault;
            if (WebServer.DEFAULT_SOCKET_NAME.equals(socket)) {
                useDefault = true;
            } else {
                ListenerConfig listenerConfig = builder.sockets().get(socket);
                if (listenerConfig == null && required) {
                    throw new IllegalArgumentException("HTTP Feature " + httpFeature + " is configured to use socket \""
                                                               + socket + "\" and it must be present, but it is not");
                }
                useDefault = listenerConfig == null;
                socket = useDefault ? WebServer.DEFAULT_SOCKET_NAME : socket;
            }
            if (useDefault) {
                HttpRouting.Builder routingBuilder;
                if (builder.routing().isPresent()) {
                    routingBuilder = builder.routing().get();
                } else {
                    routingBuilder = HttpRouting.builder();
                }

                routingBuilder.addFeature(httpFeature);

                builder.routing(routingBuilder);
            } else {
                builder.routing
                builder.routing(socket, it -> it.addFeature(httpFeature));
            }

        }
    }
}
