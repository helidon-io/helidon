/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.rsocket.server;

import java.util.HashMap;
import java.util.Map;

import io.helidon.common.reactive.Multi;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.TaggingMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


/**
 * Since currently RSocket does not have API for routing, we provide our own.
 */
public class RoutedRSocket implements RSocket {
    private final Map<String, RequestResponseHandler> requestResponseRoutes;
    private final Map<String, FireAndForgetHandler> fireAndForgetRoutes;
    private final Map<String, RequestStreamHandler> requestStreamRoutes;
    private final Map<String, RequestChannelHandler> requestChannelRoutes;
    private String mimeType = WellKnownMimeType.APPLICATION_JSON.getString();

    /**
     * Constructor for routed RSocket.
     *
     * @param requestResponseRoutes Map
     * @param fireAndForgetRoutes Map
     * @param requestStreamRoutes Map
     * @param requestChannelRoutes Map
     */
    RoutedRSocket(Map<String, RequestResponseHandler> requestResponseRoutes,
                  Map<String, FireAndForgetHandler> fireAndForgetRoutes,
                  Map<String, RequestStreamHandler> requestStreamRoutes,
                  Map<String, RequestChannelHandler> requestChannelRoutes) {
        this.requestResponseRoutes = requestResponseRoutes;
        this.fireAndForgetRoutes = fireAndForgetRoutes;
        this.requestStreamRoutes = requestStreamRoutes;
        this.requestChannelRoutes = requestChannelRoutes;
    }

    /**
     * Builder for RoutedRSocket.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Set Mime type.
     *
     * @param mimetype the mime type
     */
    public void setMimeType(String mimetype) {
        this.mimeType = mimetype;
    }

    /**
     * Builder for RoutedRSocket.
     */
    public static final class Builder {
        private Map<String, RequestResponseHandler> requestResponseRoutes;
        private Map<String, FireAndForgetHandler> fireAndForgetRoutes;
        private Map<String, RequestStreamHandler> requestStreamRoutes;
        private Map<String, RequestChannelHandler> requestChannelRoutes;

        /**
         * Constructor for Builder.
         */
        public Builder() {
            this.requestResponseRoutes = new HashMap<>();
            this.fireAndForgetRoutes = new HashMap<>();
            this.requestStreamRoutes = new HashMap<>();
            this.requestChannelRoutes = new HashMap<>();
        }

        /**
         * Set RequestResponse routes.
         *
         * @param requestResponseRoutes Map
         * @return Builder
         */
        public Builder requestResponseRoutes(Map<String, RequestResponseHandler> requestResponseRoutes) {
            this.requestResponseRoutes = requestResponseRoutes;
            return this;
        }

        /**
         * Set Fire and Forget routes.
         *
         * @param fireAndForgetRoutes Map
         * @return Builder
         */
        public Builder fireAndForgetRoutes(Map<String, FireAndForgetHandler> fireAndForgetRoutes) {
            this.fireAndForgetRoutes = fireAndForgetRoutes;
            return this;
        }

        /**
         * Set Request Stream routes.
         *
         * @param requestStreamRoutes Map
         * @return Builder
         */
        public Builder requestStreamRoutes(Map<String, RequestStreamHandler> requestStreamRoutes) {
            this.requestStreamRoutes = requestStreamRoutes;
            return this;
        }

        /**
         * Set Request Channel routes.
         *
         * @param requestChannelRoutes Map
         * @return Builder
         */
        public Builder requestChannelRoutes(Map<String, RequestChannelHandler> requestChannelRoutes) {
            this.requestChannelRoutes = requestChannelRoutes;
            return this;
        }

        /**
         * Create RoutedRSocket.
         *
         * @return {@link RoutedRSocket}
         */
        public RoutedRSocket build() {
            return new RoutedRSocket(requestResponseRoutes, fireAndForgetRoutes,
                    requestStreamRoutes, requestChannelRoutes);
        }

    }

    /**
     * Handle Request Response.
     *
     * @param payload Map
     * @return Mono with Payload
     */
    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        try {
            Map<String, TaggingMetadata> metadatas = parseMetadata(payload);
            String route = getRoute(metadatas);
            if (route == null) {
                route = "";
            }
            RequestResponseHandler handler = requestResponseRoutes.get(route);
            if (handler != null) {
                return handleRequestResponse(handler, payload);
            }
            return RSocket.super.requestResponse(payload);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }


    private Mono<Payload> handleRequestResponse(RequestResponseHandler handler, Payload payload) {
        return Mono.from(FlowAdapters.toPublisher(handler.handle(payload)));
    }

    /**
     * Handle Fire and Forget.
     *
     * @param payload Payload
     * @return Mono with Void
     */
    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        try {
            Map<String, TaggingMetadata> metadatas = parseMetadata(payload);
            String route = getRoute(metadatas);
            if (route == null) {
                route = "";
            }
            FireAndForgetHandler handler = fireAndForgetRoutes.get(route);
            if (handler != null) {
                return handleFireAndForget(handler, payload);
            }
            return RSocket.super.fireAndForget(payload);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    private Mono<Void> handleFireAndForget(FireAndForgetHandler handler, Payload payload) {
        return Mono.from(FlowAdapters.toPublisher(handler.handle(payload)));
    }

    /**
     * Handle Request Stream.
     *
     * @param payload Payload
     * @return Flux with Payload
     */
    @Override
    public Flux<Payload> requestStream(Payload payload) {
        try {
            Map<String, TaggingMetadata> metadatas = parseMetadata(payload);
            String route = getRoute(metadatas);
            if (route == null) {
                route = "";
            }
            RequestStreamHandler handler = requestStreamRoutes.get(route);
            if (handler != null) {
                return handleRequestStream(handler, payload);
            }

            return RSocket.super.requestStream(payload);
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    private Flux<Payload> handleRequestStream(RequestStreamHandler handler, Payload obj) {
        return Flux.from(FlowAdapters.toPublisher(handler.handle(obj)));
    }

    /**
     * Handle Request Channel.
     *
     * @param payloads Publisher
     * @return Flux with Payload
     */
    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        return Flux.from(payloads)
                .switchOnFirst(
                        (signal, flows) -> {
                            Payload payload = null;
                            try {
                                payload = signal.get();
                                if (payload != null) {
                                    Map<String, TaggingMetadata> metadata = parseMetadata(payload);
                                    String route = getRoute(metadata);
                                    if (route == null) {
                                        route = "";
                                    }
                                    RequestChannelHandler handler = requestChannelRoutes.get(route);
                                    if (handler != null) {
                                        return handleRequestChannel(handler, flows);
                                    }

                                }
                                return RSocket.super.requestChannel(payloads);
                            } catch (Throwable t) {
                                if (payload != null) {
                                    payload.release();
                                }
                                return Flux.error(t);
                            }
                        },
                        false);

    }

    private Flux<Payload> handleRequestChannel(RequestChannelHandler handler, Flux<Payload> payloads) {
        return Flux.from(FlowAdapters.toPublisher(
                handler.handle(Multi.create(FlowAdapters.toFlowPublisher(payloads)))));
    }

    private Map<String, TaggingMetadata> parseMetadata(Payload payload) {
        Map<String, TaggingMetadata> metadataMap = new HashMap<>();

        if (payload.hasMetadata()) {
            CompositeMetadata compositeMetadata = new CompositeMetadata(payload.metadata(), true);

            for (CompositeMetadata.Entry entry : compositeMetadata) {
                if (entry instanceof CompositeMetadata.WellKnownMimeTypeEntry) {
                    TaggingMetadata metadata = new TaggingMetadata(entry.getMimeType(), entry.getContent());

                    metadataMap.put(entry.getMimeType(), metadata);
                }
            }
        }
        return metadataMap;
    }

    private String getRoute(Map<String, TaggingMetadata> metadatas) {
        TaggingMetadata routeMetadata = metadatas.get(WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString());
        if (routeMetadata != null && routeMetadata.iterator().hasNext()) {
            return routeMetadata.iterator().next();
        }
        return null;
    }
}

