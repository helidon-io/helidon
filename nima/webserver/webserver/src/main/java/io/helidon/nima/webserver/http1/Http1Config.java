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
package io.helidon.nima.webserver.http1;

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;

/**
 * HTTP/1.1 server configuration.
 */
public class Http1Config {

    private static final int DEFAULT_MAX_PROLOGUE_LENGTH = 2048;
    private static final int DEFAULT_MAX_HEADERS_SIZE = 16384;
    private static final boolean DEFAULT_VALIDATE_HEADERS = true;
    private static final boolean DEFAULT_VALIDATE_PATH = true;

    private final int maxPrologueLength;
    private final int maxHeadersSize;
    private final boolean validateHeaders;
    private final boolean validatePath;
    private final Http1ConnectionListener sendListeners;
    private final Http1ConnectionListener recvListeners;

    private Http1Config(int maxPrologueLength,
                        int maxHeadersSize,
                        boolean validateHeaders,
                        boolean validatePath,
                        Http1ConnectionListener sendListeners,
                        Http1ConnectionListener recvListeners) {
        this.maxPrologueLength = maxPrologueLength;
        this.maxHeadersSize = maxHeadersSize;
        this.validateHeaders = validateHeaders;
        this.validatePath = validatePath;
        this.sendListeners = sendListeners;
        this.recvListeners = recvListeners;
    }

    /**
     * Maximal size of received HTTP prologue (GET /path HTTP/1.1).
     *
     * @return maximal size in bytes
     */
    public int maxPrologueLength() {
        return maxPrologueLength;
    }

    /**
     * Maximal size of received headers in bytes.
     *
     * @return maximal header size
     */
    public int maxHeadersSize() {
        return maxHeadersSize;
    }

    /**
     * Whether to validate headers.
     * If set to false, any value is accepted, otherwise validates headers + known headers
     * are validated by format
     * (content length is always validated as it is part of protocol processing (other headers may be validated if
     * features use them)).
     *
     * @return whether to validate headers
     */
    public boolean validateHeaders() {
        return validateHeaders;
    }

    /**
     * If set to false, any path is accepted (even containing illegal characters).
     *
     * @return whether to validate path
     */
    public boolean validatePath() {
        return validatePath;
    }

    // Return builder with default values
    static Builder builder() {
        return new Builder();
    }

    // Return builder with values initialized from existing configuration
    static Builder builder(Http1Config config) {
        return new Builder(config);
    }

    /**
     * Connection send event listeners for HTTP/1.1.
     *
     * @return send event listeners
     */
    public Http1ConnectionListener sendListeners() {
        return sendListeners;
    }

    /**
     * Connection receive event listeners for HTTP/1.1.
     *
     * @return receive event listeners
     */
    public Http1ConnectionListener recvListeners() {
        return recvListeners;
    }

    /**
     *  HTTP/1.1 server configuration fluent API builder.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, Http1Config> {

        private int maxPrologueLength;
        private int maxHeaderSize;
        private boolean validateHeaders;
        private boolean validatePath;
        private final List<Http1ConnectionListener> sendListeners;
        private final List<Http1ConnectionListener> recvListeners;

        private Builder() {
            this.maxPrologueLength = DEFAULT_MAX_PROLOGUE_LENGTH;
            this.maxHeaderSize = DEFAULT_MAX_HEADERS_SIZE;
            this.validateHeaders = DEFAULT_VALIDATE_HEADERS;
            this.validatePath = DEFAULT_VALIDATE_PATH;
            this.sendListeners = new LinkedList<>();
            this.recvListeners = new LinkedList<>();
        }

        private Builder(Http1Config config) {
            this.maxPrologueLength = config.maxPrologueLength;
            this.maxHeaderSize = config.maxHeadersSize;
            this.validateHeaders = config.validateHeaders;
            this.validatePath = config.validatePath;
            this.sendListeners = Http1ConnectionListenerUtil.singleListenerToList(config.sendListeners);
            this.recvListeners = Http1ConnectionListenerUtil.singleListenerToList(config.recvListeners);
        }

        /**
         * HTTP/1.1 connection provider configuration node.
         *
         * @param config configuration note to process
         * @return updated builder
         */
        public Builder config(Config config) {
            config.get("max-prologue-length").asInt().ifPresent(value -> maxPrologueLength = value);
            config.get("max-headers-size").asInt().ifPresent(value -> maxHeaderSize = value);
            config.get("validate-headers").asBoolean().ifPresent(value -> validateHeaders = value);
            config.get("validate-path").asBoolean().ifPresent(value -> validatePath = value);
            if (config.get("recv-log").asBoolean().orElse(true)) {
                addSendListener(new Http1LoggingConnectionListener("send"));
            }
            if (config.get("send-log").asBoolean().orElse(true)) {
                addReceiveListener(new Http1LoggingConnectionListener("recv"));
            }
            return this;
        }

        /**
         * Maximal size of received HTTP prologue (GET /path HTTP/1.1).
         *
         * @param maxPrologueLength maximal size in bytes
         * @return updated builder
         */
        public Builder maxPrologueLength(int maxPrologueLength) {
            this.maxPrologueLength = maxPrologueLength;
            return this;
        }

        /**
         * Maximal size of received headers in bytes.
         *
         * @param maxHeaderSize maximal header size
         * @return updated builder
         */
        public Builder maxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        /**
         * Whether to validate headers.
         * If set to false, any value is accepted, otherwise validates headers + known headers
         * are validated by format
         * (content length is always validated as it is part of protocol processing (other headers may be validated if
         * features use them)).
         *
         * @param validateHeaders whether to validate headers
         * @return updated builder
         */
        public Builder validateHeaders(boolean validateHeaders) {
            this.validateHeaders = validateHeaders;
            return this;
        }

        /**
         * If set to false, any path is accepted (even containing illegal characters).
         *
         * @param validatePath whether to validate path
         * @return updated builder
         */
        public Builder validatePath(boolean validatePath) {
            this.validatePath = validatePath;
            return this;
        }

        /**
         * Add a send event listener.
         *
         * @param listener listener to add
         * @return updated builder
         */
        public Builder addSendListener(Http1ConnectionListener listener) {
            sendListeners.add(listener);
            return this;
        }

        /**
         * Add a receive event listener.
         *
         * @param listener listener to add
         * @return updated builder
         */
        public Builder addReceiveListener(Http1ConnectionListener listener) {
            recvListeners.add(listener);
            return this;
        }

        @Override
        public Http1Config build() {
            return new Http1Config(
                    maxPrologueLength,
                    maxHeaderSize,
                    validateHeaders,
                    validatePath,
                    Http1ConnectionListener.create(sendListeners),
                    Http1ConnectionListener.create(recvListeners)
            );
        }

    }

}
