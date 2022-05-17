/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.webserver;

import java.util.Optional;

/**
 * Service providing HTTP upgrade codec for Helidon webserver.
 */
public interface UpgradeCodecSupplier {

    /**
     * Name of the protocol expected in {@code Upgrade} header during HTTP upgrade request
     * for using decoder provided by this supplier.
     *
     * @return protocol name
     */
    CharSequence clearTextProtocol();

    /**
     * Name of the protocol expected by ALPN negotiation for using this protocol,
     * prior-knowledge decoder is expected to be used.
     *
     * @return name of the protocol supported by ALPN or empty optional
     */
    Optional<String> tlsProtocol();

    /**
     * Codec used by the {@link io.netty.handler.codec.http.HttpServerUpgradeHandler HttpServerUpgradeHandler}
     * when {@link UpgradeCodecSupplier#clearTextProtocol() clearTextProtocol()} matches.
     *
     * @param sourceCodec      For replacing HttpResponseEncoder and HttpRequestDecoder when using
     *                         {@link io.netty.handler.codec.http.HttpServerUpgradeHandler HttpServerUpgradeHandler}
     * @param router          set of all configured routings
     * @param maxContentLength maximum length of the content of an upgrade request
     * @param <A> Source codec type
     * @param <R> Upgrade coded type
     * @return upgrade codec
     */
    <R, A> R upgradeCodec(A sourceCodec,
                          Router router,
                          int maxContentLength);

    /**
     * Used as a wrapper for actual upgrade handler, if available.
     * Provides prior-knowledge capability in case other side decides to skip HTTP upgrade.
     *
     * @param sourceCodec           For replacing HttpResponseEncoder and HttpRequestDecoder when using
     *                              {@link io.netty.handler.codec.http.HttpServerUpgradeHandler HttpServerUpgradeHandler}
     * @param wrappedUpgradeHandler Actual upgrade handler used when prior-knowledge doesn't kick in
     * @param maxContentLength      maximum length of the content of an upgrade request
     * @param <A> Source codec type
     * @param <B> Upgrade handler type
     * @param <R> Result prior knowledge handler type
     * @return prior-knowledge decoder or empty optional
     */
    default <R, A, B> Optional<R> priorKnowledgeDecoder(A sourceCodec,
                                                        B wrappedUpgradeHandler,
                                                        int maxContentLength) {
        return Optional.empty();
    }
}
