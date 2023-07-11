/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http2.webclient;

import io.helidon.nima.http2.WindowSize;
import io.helidon.nima.webclient.api.DefaultDnsResolverProvider;
import io.helidon.nima.webclient.api.DnsAddressLookup;
import io.helidon.nima.webclient.api.HttpClient;
import io.helidon.nima.webclient.api.WebClient;

/**
 * HTTP2 client.
 */
public interface Http2Client extends HttpClient<Http2ClientRequest, Http2ClientResponse> {

    /**
     * A new fluent API builder to customize client setup.
     *
     * @return a new builder
     */
    static Http2ClientBuilder builder() {
        return new Http2ClientBuilder();
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.http2.webclient.Http2Client}.
     */
    class Http2ClientBuilder extends WebClient.Builder<Http2ClientBuilder, Http2Client> {

        private boolean priorKnowledge;
        private int maxFrameSize = WindowSize.DEFAULT_MAX_FRAME_SIZE;
        private long maxHeaderListSize = -1;
        private int initialWindowSize = WindowSize.DEFAULT_WIN_SIZE;
        private int prefetch = 33554432;

        private Http2ClientBuilder() {
            // until we use the same parent for HTTP/1 and HTTP/2, we need to have these defined as defaults
            super.dnsResolver(new DefaultDnsResolverProvider().createDnsResolver());
            super.dnsAddressLookup(DnsAddressLookup.defaultLookup());
        }

        /**
         * Prior knowledge of HTTP/2 capabilities of the server. If server we are connecting to does not
         * support HTTP/2 and prior knowledge is set to {@code false}, only features supported by HTTP/1 will be available
         * and attempts to use HTTP/2 specific will throw an {@link UnsupportedOperationException}.
         * <h4>Plain text connection</h4>
         * If prior knowledge is set to {@code true}, we will not attempt an upgrade of connection and use prior knowledge.
         * If prior knowledge is set to {@code false}, we will initiate an HTTP/1 connection and upgrade it to HTTP/2,
         * if supported by the server.
         * plaintext connection ({@code h2c}).
         * <h4>TLS protected connection</h4>
         * If prior knowledge is set to {@code true}, we will negotiate protocol using HTTP/2 only, failing if not supported.
         * if prior knowledge is set to {@code false}, we will negotiate protocol using both HTTP/2 and HTTP/1, using the protocol
         * supported by server.
         *
         * @param priorKnowledge whether to use prior knowledge of HTTP/2
         * @return updated client
         */
        public Http2ClientBuilder priorKnowledge(boolean priorKnowledge) {
            this.priorKnowledge = priorKnowledge;
            return this;
        }

        /**
         * Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections.
         * Maximum size of data frames in bytes the client is prepared to accept from the server.
         * Default value is 2^14(16_384).
         *
         * @param maxFrameSize data frame size in bytes between 2^14(16_384) and 2^24-1(16_777_215)
         * @return updated client
         */
        public Http2ClientBuilder maxFrameSize(int maxFrameSize) {
            if (maxFrameSize < WindowSize.DEFAULT_MAX_FRAME_SIZE || maxFrameSize > WindowSize.MAX_MAX_FRAME_SIZE) {
                throw new IllegalArgumentException(
                        "Max frame size needs to be a number between 2^14(16_384) and 2^24-1(16_777_215)"
                );
            }
            this.maxFrameSize = maxFrameSize;
            return this;
        }

        /**
         * Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections.
         * Sends to the server the maximum header field section size client is prepared to accept.
         *
         * @param maxHeaderListSize units of octets
         * @return updated client
         */
        public Http2ClientBuilder maxHeaderListSize(long maxHeaderListSize){
            this.maxHeaderListSize = maxHeaderListSize;
            return this;
        }

        /**
         * Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections.
         * Sends to the server the size of the largest frame payload client is willing to receive.
         *
         * @param initialWindowSize units of octets
         * @return updated client
         */
        public Http2ClientBuilder initialWindowSize(int initialWindowSize){
            this.initialWindowSize = initialWindowSize;
            return this;
        }

        /**
         * First connection window update increment sent right after the connection is established.
         *
         * @param prefetch number of bytes the client is prepared to receive as data from all the streams combined
         * @return updated client
         */
        public Http2ClientBuilder prefetch(int prefetch) {
            this.prefetch = prefetch;
            return this;
        }

        @Override
        public Http2Client doBuild() {
            return new Http2ClientImpl(this);
        }

        boolean priorKnowledge() {
            return priorKnowledge;
        }

        long maxHeaderListSize() {
            return maxHeaderListSize;
        }

        int initialWindowSize() {
            return initialWindowSize;
        }

        int prefetch() {
            return prefetch;
        }

        int maxFrameSize() {
            return this.maxFrameSize;
        }
    }
}
