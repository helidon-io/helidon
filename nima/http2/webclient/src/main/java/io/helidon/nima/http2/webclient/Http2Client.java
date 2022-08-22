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
 */

package io.helidon.nima.http2.webclient;

import io.helidon.nima.webclient.HttpClient;
import io.helidon.nima.webclient.WebClient;

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

        private Http2ClientBuilder() {
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

        @Override
        public Http2Client build() {
            return new Http2ClientImpl(this);
        }

        boolean priorKnowledge() {
            return priorKnowledge;
        }
    }
}
