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

import java.util.Objects;

import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
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
    @Configured
    class Http1ClientBuilder extends WebClient.Builder<Http1ClientBuilder, Http1Client> {
        private int maxHeaderSize = 16384;
        private int maxStatusLineLength = 256;
        // temporarily disable to investigate a test issue in KeepAliveTest
        private boolean sendExpect100Continue = false;
        private boolean validateHeaders = true;
        private MediaContext mediaContext = MediaContext.create();
        private int connectionQueueSize = 16384;

        private Http1ClientBuilder() {
        }

        /**
         * Configure the maximum allowed header size of the response.
         *
         * @param maxHeaderSize maximum header size
         * @return updated builder
         */
        @ConfiguredOption("16384")
        public Http1ClientBuilder maxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        /**
         * Configure the maximum allowed length of the status line from the response.
         *
         * @param maxStatusLineLength maximum status line length
         * @return updated builder
         */
        @ConfiguredOption("256")
        public Http1ClientBuilder maxStatusLineLength(int maxStatusLineLength) {
            this.maxStatusLineLength = maxStatusLineLength;
            return this;
        }

        /**
         * Sets whether Expect-100-Continue header is sent to verify server availability for a chunked transfer.
         * <p>
         *     Defaults to {@code true}.
         * </p>
         *
         * @param sendExpect100Continue whether Expect:100-Continue header should be sent on chunked transfers
         * @return updated builder
         */
        @ConfiguredOption("true")
        public Http1ClientBuilder sendExpect100Continue(boolean sendExpect100Continue) {
            this.sendExpect100Continue = sendExpect100Continue;
            return this;
        }

        /**
         * Sets whether the header format is validated or not.
         * <p>
         *     Defaults to {@code true}.
         * </p>
         *
         * @param validateHeaders whether header validation should be enabled
         * @return updated builder
         */
        @ConfiguredOption("true")
        public Http1ClientBuilder validateHeaders(boolean validateHeaders) {
            this.validateHeaders = validateHeaders;
            return this;
        }

        /**
         * Configure the default {@link MediaContext}.
         *
         * @param mediaContext media context for this client
         * @return updated builder
         */
        @ConfiguredOption("io.helidon.nima.http.media.MediaContext")
        public Http1ClientBuilder mediaContext(MediaContext mediaContext) {
            Objects.requireNonNull(mediaContext);
            this.mediaContext = mediaContext;
            return this;
        }

        /**
         * Configure the maximum allowed size of the connection queue.
         *
         * @param connectionQueueSize maximum connection queue size
         * @return updated builder
         */
        @ConfiguredOption("10")
        public Http1ClientBuilder connectionQueueSize(int connectionQueueSize) {
            this.connectionQueueSize = connectionQueueSize;
            return this;
        }

        /**
         * Maximum allowed header size of the response.
         *
         * @return maximum header size
         */
        public int maxHeaderSize() {
            return maxHeaderSize;
        }

        /**
         * Maximum allowed length of the status line from the response.
         *
         * @return maximum status line length
         */
        public int maxStatusLineLength() {
            return maxStatusLineLength;
        }

        /**
         * Indicates whether Expect:100-Continue header will be sent to verify server availability for chunked transfers.
         *
         * @return whether to send Expect:100-Continue header for chunked transfers
         */
        public boolean sendExpect100Continue() {
            return sendExpect100Continue;
        }

        /**
         * Indicates whether the header format is validated or not.
         *
         * @return whether to validate headers
         */
        public boolean validateHeaders() {
            return validateHeaders;
        }

        /**
         * Media context of this client.
         *
         * @return media context, never {@code null}
         */
        public MediaContext mediaContext() {
            return mediaContext;
        }

        /**
         * Maximum allowed size of the connection queue.
         *
         * @return maximum queue size
         */
        public int connectionQueueSize() {
            return connectionQueueSize;
        }

        @Override
        public Http1Client build() {
            return new Http1ClientImpl(this);
        }
    }

}
