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

package io.helidon.rsocket.client;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.AuthMetadataCodec;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.TaggingMetadata;
import io.rsocket.metadata.TaggingMetadataCodec;
import io.rsocket.metadata.WellKnownAuthType;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.util.DefaultPayload;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;


/**
 * Helidon RSocket client.
 */
public class RSocketClient implements Disposable {
    private io.rsocket.core.RSocketClient client;
    private String route;
    private WellKnownAuthType authType = null;
    private String metadataMimeType;
    private String mimeType;
    private String username;
    private String password;
    private String token;


    public static Builder builder() {
        return new Builder();
    }
    /**
     * RSocket Client constructor.
     *
     * @param client RSocketClient.
     */
    private RSocketClient(io.rsocket.core.RSocketClient client) {
        this.client = client;
    }

    public static RSocketClient create(Config config){
        return builder().config(config).build();
    }

    /**
     * Set route.
     *
     * @param route String.
     */
    public void route(String route) {
        this.route = route;
    }

    /**
     * Set MIME type.
     *
     * @param mimeType String.
     */
    public void setOneTimeMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Set source.
     *
     * @return Single with RSocket.
     */
    public Single<RSocket> source() {
        Mono<RSocket> source = client.source();
        return Single.create(FlowAdapters.toFlowPublisher(source));
    }

    /**
     * Set auth token.
     *
     * @param token String
     */
    public void authBearer(String token) {
        this.token = token;
        this.authType = WellKnownAuthType.BEARER;
    }

    /**
     * Set simple Auth credentials.
     *
     * @param username
     * @param password
     */
    public void authSimple(String username, String password) {
        this.username = username;
        this.password = password;
        this.authType = WellKnownAuthType.SIMPLE;
    }

    private CompositeByteBuf getMetadata() {
        CompositeByteBuf metadata = ByteBufAllocator.DEFAULT.compositeBuffer();
        if (route != null) {
            RoutingMetadata routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT,
                    Collections.singletonList(route));
            CompositeMetadataCodec.encodeAndAddMetadata(metadata,
                    ByteBufAllocator.DEFAULT,
                    WellKnownMimeType.MESSAGE_RSOCKET_ROUTING,
                    routingMetadata.getContent());
        }

        if (mimeType != null) {
//            TaggingMetadata mimeMetadata = TaggingMetadataCodec.createTaggingMetadata(ByteBufAllocator.DEFAULT,
//                    WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE.getString(), Collections.singletonList(mimeType));
            CompositeMetadataCodec.encodeAndAddMetadata(metadata,
                    ByteBufAllocator.DEFAULT,
                    WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE,
                    ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, mimeType));
            //reset per stream mime type
            //mimeType = null;
        }
        if (authType == WellKnownAuthType.SIMPLE) {
            ByteBuf byteBuf = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT,
                    username.toCharArray(), password.toCharArray());
            CompositeMetadataCodec.encodeAndAddMetadata(metadata,
                    ByteBufAllocator.DEFAULT,
                    WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION,
                    byteBuf);
        }
        if (authType == WellKnownAuthType.BEARER) {
            ByteBuf byteBuf = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT,
                    token.toCharArray());
            CompositeMetadataCodec.encodeAndAddMetadata(metadata,
                    ByteBufAllocator.DEFAULT,
                    WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION,
                    byteBuf);
        }
        return metadata;
    }

    /**
     * Send Data via Fire and Forget method.
     *
     * @param dataSingle
     * @return Single
     */
    public Single<Void> fireAndForget(Single<ByteBuffer> dataSingle) {
        Mono<Void> resultMono = client.fireAndForget(Mono
                .from(FlowAdapters.toPublisher(dataSingle))
                .map(data -> DefaultPayload.create(data, getMetadata().nioBuffer())));
        return Single.create(FlowAdapters.toFlowPublisher(resultMono));
    }

    /**
     * Send Data via Request/Response method.
     *
     * @param dataSingle
     * @return Single with Payload.
     */
    public Single<Payload> requestResponse(Single<ByteBuffer> dataSingle) {
        Mono<Payload> payloadMono = client.requestResponse(Mono
                .from(FlowAdapters.toPublisher(dataSingle))
                .map(data -> DefaultPayload.create(data, getMetadata().nioBuffer())));
        return Single.create(FlowAdapters.toFlowPublisher(payloadMono));
    }

    /**
     * Send data via Request Stream.
     *
     * @param dataSingle
     * @return Multi with payload.
     */
    public Multi<Payload> requestStream(Single<ByteBuffer> dataSingle) {
        Flux<Payload> payloadFlux = client.requestStream(Mono
                .from(FlowAdapters.toPublisher(dataSingle))
                .map(data -> DefaultPayload.create(data, getMetadata().nioBuffer())));
        return Multi.create(FlowAdapters.toFlowPublisher(payloadFlux));

    }

    /**
     * Send data via Request Channel.
     *
     * @param data
     * @return Multi with payload.
     */
    public Multi<Payload> requestChannel(Publisher<ByteBuffer> data) {
        Flux<Payload> payloadFlux = client.requestChannel(Flux
                .from(data).map(d -> DefaultPayload.create(d, getMetadata().nioBuffer())));
        return Multi.create(FlowAdapters.toFlowPublisher(payloadFlux));
    }

    /**
     * Push metadata.
     *
     * @param dataSingle
     * @return Single.
     */
    public Single<Void> metadataPush(Single<ByteBuffer> dataSingle) {
        Mono<Void> voidMono = client.metadataPush(Mono
                .from(FlowAdapters.toPublisher(dataSingle))
                .map(data -> DefaultPayload.create(data, getMetadata().nioBuffer())));
        return Single.create(FlowAdapters.toFlowPublisher(voidMono));
    }

    /**
     * Dispose the Client.
     */
    public void dispose() {
        client.dispose();
    }


    public static class Builder implements io.helidon.common.Builder<RSocketClient> {

        private String route;
        private WellKnownAuthType authType = null;
        private String mimeType = WellKnownMimeType.TEXT_PLAIN.toString();
        private String metadataMimeType=WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.toString();
        private String username;
        private String password;
        private String token;
        private String websocket;
        private String uri;
        private int port = 9090;

        @Override
        public RSocketClient build() {
            RSocket rSocket;
            if (uri != null && !uri.isEmpty()) {
                rSocket = io.rsocket.core.RSocketConnector.create()
                        .dataMimeType(mimeType)
                        .metadataMimeType(metadataMimeType)
                        .connect(WebsocketClientTransport.create(URI.create(websocket)))
                        .block();
            } else {
                rSocket = io.rsocket.core.RSocketConnector.create()
                        .dataMimeType(mimeType)
                        .metadataMimeType(metadataMimeType)
                        .connect(TcpClientTransport.create(uri,port))
                        .block();
            }

            io.rsocket.core.RSocketClient client = io.rsocket.core.RSocketClient.from(rSocket);
            RSocketClient result = new RSocketClient(client);

            if (route != null || !route.isEmpty()) {
                result.route = route;
                result.authType = authType;
                result.mimeType = mimeType;
                result.username = username;
                result.password = password;
                result.token = token;

            }
            return result;
        }

        /**
         * Get setup data from the configuration.
         *
         * @param config
         * @return Build
         */
        public Builder config(Config config) {
            config.get("authentication.username").asString().ifPresent(this::username);
            config.get("authentication.password").asString().ifPresent(this::password);
            config.get("route").asString().ifPresent(this::route);
            config.get("websocket").asString().ifPresent(this::websocket);
            config.get("mimeType").asString().ifPresent(this::mimeType);
            config.get("metadataMimeType").asString().ifPresent(this::metadataMimeType);
            config.get("uri").asString().ifPresent(this::uri);
            config.get("port").asInt().ifPresent(this::port);

            return this;
        }

        public Builder route(String route) {
            this.route = route;
            return this;
        }

        public Builder authType(WellKnownAuthType authType) {
            this.authType = authType;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder metadataMimeType(String metadataMimeType) {
            this.metadataMimeType = metadataMimeType;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder websocket(String websocket) {
            this.websocket = websocket;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }
    }
}
