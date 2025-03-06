/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.spi.ErrorHandlerProvider;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerFeature;

/*
Combines configuration done by hand with services discovered using the registry.
 */
@Service.Singleton
class WebServerService {
    private final Optional<Config> config;
    private final List<ServerFeature> serverFeatures;
    private final List<HttpFeature> httpFeatures;
    private final List<ProtocolConfig> protocolConfigs;
    private final List<ContentEncoding> contentEncodings;
    private final List<MediaSupport> mediaSupports;
    private final List<ServerConnectionSelector> connectionSelectors;
    private final List<ErrorHandlerProvider<?>> httpErrorHandlers;
    private final Optional<DirectHandlers> directHandlers;
    private final Optional<RequestedUriDiscoveryContext> requestedUriDiscoveryContext;

    @Service.Inject
    WebServerService(Optional<Config> config,
                     List<ServerFeature> serverFeatures,
                     List<HttpFeature> httpFeatures,
                     List<ProtocolConfig> protocolConfigs,
                     List<ContentEncoding> contentEncodings,
                     List<MediaSupport> mediaSupports,
                     List<ServerConnectionSelector> connectionSelectors,
                     List<ErrorHandlerProvider<?>> httpErrorHandlers,
                     Optional<DirectHandlers> directHandlers,
                     Optional<RequestedUriDiscoveryContext> requestedUriDiscoveryContext) {

        this.config = config;
        this.serverFeatures = serverFeatures;
        this.httpFeatures = httpFeatures;
        this.protocolConfigs = protocolConfigs;
        this.contentEncodings = contentEncodings;
        this.mediaSupports = mediaSupports;
        this.connectionSelectors = connectionSelectors;
        this.directHandlers = directHandlers;
        this.httpErrorHandlers = httpErrorHandlers;
        this.requestedUriDiscoveryContext = requestedUriDiscoveryContext;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
        HttpRouting.Builder defaultRoutingBuilder = builder.routing()
                .orElseGet(HttpRouting::builder);
        builder.routing(defaultRoutingBuilder);

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
                defaultRoutingBuilder.addFeature(httpFeature);
            } else {
                // TODO update routing of a specific socket that does not remove existing routes
                // this may require custom builder...
                // builder.routing(socket, it -> it.addFeature(httpFeature));
            }
        }

        for (ErrorHandlerProvider httpErrorHandler : httpErrorHandlers) {
            defaultRoutingBuilder.error(httpErrorHandler.errorType(),
                                        httpErrorHandler.create());
        }

        directHandlers.ifPresent(builder::directHandlers);
    }
}
