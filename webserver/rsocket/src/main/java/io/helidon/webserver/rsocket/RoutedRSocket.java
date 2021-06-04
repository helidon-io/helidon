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

package io.helidon.webserver.rsocket;

import java.util.HashMap;
import java.util.Map;


import io.helidon.common.reactive.Multi;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.TaggingMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RoutedRSocket implements RSocket {
    private final Map<String, RequestResponseHandler<?>> requestResponseRoutes;
    private final Map<String, FireAndForgetHandler> fireAndForgetRoutes;
    private final Map<String, RequestStreamHandler<?>> requestStreamRoutes;
    private final Map<String, RequestChannelHandler<?>> requestChannelRoutes;
    private String mimeType = WellKnownMimeType.APPLICATION_JSON.getString();

    RoutedRSocket(Map<String, RequestResponseHandler<?>> requestResponseRoutes,
                  Map<String, FireAndForgetHandler> fireAndForgetRoutes,
                  Map<String, RequestStreamHandler<?>> requestStreamRoutes,
                  Map<String, RequestChannelHandler<?>> requestChannelRoutes) {
        this.requestResponseRoutes = requestResponseRoutes;
        this.fireAndForgetRoutes = fireAndForgetRoutes;
        this.requestStreamRoutes = requestStreamRoutes;
        this.requestChannelRoutes = requestChannelRoutes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void setMimeType(String mimetype) {
        this.mimeType = mimetype;
    }

    public boolean isAuthValid(ConnectionSetupPayload setupPayload) {
        //TODO authentication...
        return true;
    }

    public static final class Builder {
        private Map<String, RequestResponseHandler<?>> requestResponseRoutes;
        private Map<String, FireAndForgetHandler> fireAndForgetRoutes;
        private Map<String, RequestStreamHandler<?>> requestStreamRoutes;
        private Map<String, RequestChannelHandler<?>> requestChannelRoutes;

        public Builder() {
            this.requestResponseRoutes = new HashMap<>();
            this.fireAndForgetRoutes = new HashMap<>();
            this.requestStreamRoutes = new HashMap<>();
            this.requestChannelRoutes = new HashMap<>();
        }

        public Builder requestResponseRoutes(Map<String, RequestResponseHandler<?>> requestResponseRoutes) {
            this.requestResponseRoutes = requestResponseRoutes;
            return this;
        }

        public Builder fireAndForgetRoutes(Map<String, FireAndForgetHandler> fireAndForgetRoutes) {
            this.fireAndForgetRoutes = fireAndForgetRoutes;
            return this;
        }

        public Builder requestStreamRoutes(Map<String, RequestStreamHandler<?>> requestStreamRoutes) {
            this.requestStreamRoutes = requestStreamRoutes;
            return this;
        }


        public Builder requestChannelRoutes(Map<String, RequestChannelHandler<?>> requestChannelRoutes) {
            this.requestChannelRoutes = requestChannelRoutes;
            return this;
        }

        public RoutedRSocket build() {
            return new RoutedRSocket(requestResponseRoutes, fireAndForgetRoutes, requestStreamRoutes, requestChannelRoutes);
        }

    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        try {
            Map<String, TaggingMetadata> metadatas = parseMetadata(payload);
            String route = getRoute(metadatas);
            if (route != null) {

                RequestResponseHandler<?> handler = requestResponseRoutes.get(route);
                if (handler != null) {
                    return handleRequestResponse(handler, payload);
                }
            }
            return RSocket.super.requestResponse(payload);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }


    private <T> Mono<Payload> handleRequestResponse(RequestResponseHandler<T> handler, Object obj) {
        return (Mono<Payload>) handler.handle((T) obj);
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        try {
            Map<String, TaggingMetadata> metadatas = parseMetadata(payload);
            String route = getRoute(metadatas);
            if (route != null) {
                FireAndForgetHandler handler = fireAndForgetRoutes.get(route);
                if (handler != null) {
                    return handleFireAndForget(handler, payload);
                }
            }
            return RSocket.super.fireAndForget(payload);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    private Mono<Void> handleFireAndForget(FireAndForgetHandler handler, Payload payload) {
        return Mono.from(FlowAdapters.toPublisher(handler.handle(payload)));
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        try {
            Map<String, TaggingMetadata> metadatas = parseMetadata(payload);
            String route = getRoute(metadatas);
            if (route != null) {
                RequestStreamHandler<?> handler = requestStreamRoutes.get(route);
                if (handler != null) {
                    return handleRequestStream(handler, payload);
                }
            }
            return RSocket.super.requestStream(payload);
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    private Flux<Payload> handleRequestStream(RequestStreamHandler handler, Payload obj) {
        return Flux.from(FlowAdapters.toPublisher(handler.handle(obj)));
    }

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
                                    if (route != null) {
                                        RequestChannelHandler<?> handler = requestChannelRoutes.get(route);
                                        if (handler != null) {
                                            return handleRequestChannel(handler, flows);
                                        }
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
        return Flux.from(FlowAdapters.toPublisher(handler.handle(Multi.create(FlowAdapters.toFlowPublisher(payloads)))));
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

