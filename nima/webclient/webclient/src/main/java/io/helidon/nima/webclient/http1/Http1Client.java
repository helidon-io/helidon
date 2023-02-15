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

package io.helidon.nima.webclient.http1;

import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.HttpClient;
import io.helidon.nima.webclient.WebClient;

/**
 * HTTP/1.1 client.
 */
public interface Http1Client extends HttpClient<Http1ClientRequest, Http1ClientResponse> {
    /**
     * A new fluent API builder to customize instances.
     *
     * @return a new builder
     */
    static Http1ClientBuilder builder() {
        return new Http1ClientBuilder();
    }

    /**
     * Builder for {@link io.helidon.nima.webclient.http1.Http1Client}.
     */
    class Http1ClientBuilder extends WebClient.Builder<Http1ClientBuilder, Http1Client> {
        private int maxHeaderSize = 16384;
        private int maxStatusLineLength = 256;
        private boolean sendExpect100Continue = true;
        private boolean validateHeaders = true;
        private MediaContext mediaContext = MediaContext.create();

        private Http1ClientBuilder() {
        }

        /**
         * Maximum Header Size.
         *
         * @param maxHeaderSize Maximum Header Size
         * @return updated builder
         */
        public Http1ClientBuilder maxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        public Http1ClientBuilder maxStatusLineLength(int maxStatusLineLength) {
            this.maxStatusLineLength = maxStatusLineLength;
            return this;
        }

        public Http1ClientBuilder sendExpect100Continue(boolean sendExpect100Continue) {
            this.sendExpect100Continue = sendExpect100Continue;
            return this;
        }

        public Http1ClientBuilder validateHeaders(boolean validateHeaders) {
            this.validateHeaders = validateHeaders;
            return this;
        }

        public Http1ClientBuilder mediaContext(MediaContext mediaContext) {
            this.mediaContext = mediaContext;
            return this;
        }

        /**
         * Configured Max Header Size.
         *
         * @return maxHeaderSize Maximum Header Size
         */
        public int maxHeaderSize() {
            return maxHeaderSize;
        }

        public int maxStatusLineLength() {
            return maxStatusLineLength;
        }

        public boolean sendExpect100Continue() {
            return sendExpect100Continue;
        }

        public boolean validateHeaders() {
            return validateHeaders;
        }

        public MediaContext mediaContext() {
            return mediaContext;
        }

        @Override
        public Http1Client build() {
            return new Http1ClientImpl(this);
        }
    }

}
