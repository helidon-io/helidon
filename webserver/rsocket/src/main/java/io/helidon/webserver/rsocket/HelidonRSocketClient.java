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

import java.nio.ByteBuffer;
import java.util.Collections;

import org.reactivestreams.Publisher;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketClient;
import io.rsocket.metadata.AuthMetadataCodec;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.TaggingMetadata;
import io.rsocket.metadata.TaggingMetadataCodec;
import io.rsocket.metadata.WellKnownAuthType;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.DefaultPayload;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Helidon RSocket client.
 */
public class HelidonRSocketClient implements Disposable {
    private RSocketClient client;
    private String mimeType;
    private String route;
    private WellKnownAuthType authType = null;
    private String username;
    private String password;
    private String token;

    /**
     * RSocket Client constructor.
     *
     * @param client RSocketClient.
     */
    public HelidonRSocketClient(RSocketClient client) {
        this.client = client;
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
     * @return Mono with RSocket.
     */
    public Mono<RSocket> source() {
        return client.source();
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
            TaggingMetadata mimeMetadata = TaggingMetadataCodec.createTaggingMetadata(ByteBufAllocator.DEFAULT,
                    WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE.getString(), Collections.singletonList(mimeType));
            CompositeMetadataCodec.encodeAndAddMetadata(metadata,
                    ByteBufAllocator.DEFAULT,
                    WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE,
                    mimeMetadata.getContent());
            //reset per stream mime type
            mimeType = null;
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
     * @param dataMono
     * @return Mono
     */
    public Mono<Void> fireAndForget(Mono<ByteBuffer> dataMono) {
        return client.fireAndForget(dataMono.map(data -> {
            return DefaultPayload.create(data, getMetadata().nioBuffer());
        }));
    }

    /**
     *
     * Send Data via Request/Response method.
     *
     * @param dataMono
     * @return Mono with Payload.
     */
    public Mono<Payload> requestResponse(Mono<ByteBuffer> dataMono) {
        return client.requestResponse(dataMono.map(data -> {
            return DefaultPayload.create(data, getMetadata().nioBuffer());
        }));
    }

    /**
     * Send data via Request Stream.
     *
     * @param dataMono
     * @return Flux with payload.
     */
    public Flux<Payload> requestStream(Mono<ByteBuffer> dataMono) {
        return client.requestStream(dataMono.map(data -> {
            return DefaultPayload.create(data, getMetadata().nioBuffer());
        }));
    }

    /**
     * Send data via Request Channel.
     *
     * @param data
     * @return Flux with payload.
     */
    public Flux<Payload> requestChannel(Publisher<ByteBuffer> data) {
        return client.requestChannel(Flux.from(data).map(d-> {
            return DefaultPayload.create(d, getMetadata().nioBuffer());
        }));
    }

    /**
     * Push metadata.
     * @param dataMono
     * @return Mono.
     */
    public Mono<Void> metadataPush(Mono<ByteBuffer> dataMono) {
        return client.metadataPush(dataMono.map(data -> {
            return DefaultPayload.create(data, getMetadata().nioBuffer());
        }));
    }

    /**
     * Dispose the Client.
     */
    public void dispose() {
        client.dispose();
    }
}
