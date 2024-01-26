package io.helidon.webserver;

import java.util.List;
import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.ContentEncodingContextConfig;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaContextConfig;
import io.helidon.http.media.MediaSupport;
import io.helidon.inject.service.Injection;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerFeature;

/*
Combines configuration done by hand with services discovered using the registry.
 */
@Injection.Singleton
class WebServerService {
    private final Optional<Config> config;
    private final List<ServerFeature> serverFeatures;
    private final List<HttpFeature> httpFeatures;
    private final List<ProtocolConfig> protocolConfigs;
    private final List<ContentEncoding> contentEncodings;
    private final List<MediaSupport> mediaSupports;
    private final List<ServerConnectionSelector> connectionSelectors;
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
        this.requestedUriDiscoveryContext = requestedUriDiscoveryContext;
    }

    void updateServerBuilder(WebServerConfig.BuilderBase<?, ?> builder) {
        if (builder.config().isEmpty()) {
            config.map(it -> it.get("server")).ifPresent(builder::config);
        }
        if (builder.requestedUriDiscoveryContext().isEmpty()) {
            requestedUriDiscoveryContext.ifPresent(builder::requestedUriDiscoveryContext);
        }

        protocolConfigs.forEach(builder::addProtocol);
        if (!contentEncodings.isEmpty()) {
            Optional<ContentEncodingContext> ctx = builder.contentEncoding();
            ContentEncodingContextConfig.Builder ctxBuilder = ContentEncodingContext.builder();
            ctx.ifPresent(contentEncodingContext -> ctxBuilder.from(contentEncodingContext.prototype()));
            builder.contentEncoding(ctxBuilder.update(it -> contentEncodings.forEach(it::addContentEncoding))
                        .build());

        }
        if (!mediaSupports.isEmpty()) {
            Optional<MediaContext> ctx = builder.mediaContext();
            MediaContextConfig.Builder ctxBuilder = MediaContextConfig.builder();
            ctx.ifPresent(mediaContext -> ctxBuilder.from(mediaContext.prototype()));
            builder.mediaContext(ctxBuilder.update(it -> mediaSupports.forEach(it::addMediaSupport))
                                            .build());
        }
        connectionSelectors.forEach(builder::addConnectionSelector);
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
                // TODO update routing of a specific socket that does not remove existing routes
                // this may require custom builder...
                // builder.routing(socket, it -> it.addFeature(httpFeature));
            }

        }
    }
}
