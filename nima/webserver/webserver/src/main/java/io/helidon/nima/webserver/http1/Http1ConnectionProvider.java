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

package io.helidon.nima.webserver.http1;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.config.Config;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for HTTP/1.1 server connection provider.
 */
public class Http1ConnectionProvider implements ServerConnectionProvider {
    private static final String PROTOCOL = " HTTP/1.1\r";
    private static final int DEFAULT_MAX_PROLOGUE_LENGTH = 2048;
    private static final int DEFAULT_MAX_HEADERS_SIZE = 8096;
    private static final boolean DEFAULT_VALIDATE_HEADERS = true;
    private static final boolean DEFAULT_VALIDATE_PATH = true;

    private final int maxPrologueLength;
    private final int maxHeadersSize;
    private final boolean validateHeaders;
    private final boolean validatePath;
    private final Map<String, Http1UpgradeProvider> upgradeProviderMap;
    private final Http1ConnectionListener sendListener;
    private final Http1ConnectionListener recvListener;

    /**
     * Create a new instance with default configuration.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public Http1ConnectionProvider() {
        this(builder());
    }

    private Http1ConnectionProvider(Builder builder) {
        this.maxPrologueLength = builder.maxPrologueLength;
        this.maxHeadersSize = builder.maxHeaderSize;
        this.validateHeaders = builder.validateHeaders;
        this.validatePath = builder.validatePath;
        this.upgradeProviderMap = builder.upgradeProviders();
        this.sendListener = builder.sendListener();
        this.recvListener = builder.recvListener();
    }

    /**
     * Builder to set up this provider.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int bytesToIdentifyConnection() {
        // the request must begin with
        return 0;
    }

    @Override
    public Support supports(BufferData request) {
        // we are looking for first \n, if preceded by \r -> try if ours, otherwise not supported

        /*
        > GET /loom/slow HTTP/1.1
        > Host: localhost:8080
        > User-Agent: curl/7.54.0
        > Accept: * /*
         */

        int lf = request.indexOf(Bytes.LF_BYTE);
        if (lf == -1) {
            // in case we have reached the max prologue length, we just consider this to be HTTP/1.1 so we can send
            // proper error. This means that maxPrologueLength should always be higher than any protocol requirements to
            // identify a connection (e.g. this is the fallback protocol)
            return (request.available() <= maxPrologueLength) ? Support.SUPPORTED : Support.UNSUPPORTED;
        } else {
            return request.readString(lf).endsWith(PROTOCOL) ? Support.SUPPORTED : Support.UNSUPPORTED;
        }
    }

    @Override
    public Set<String> supportedApplicationProtocols() {
        return Set.of("http/1.1");
    }

    @Override
    public ServerConnection connection(ConnectionContext ctx) {
        return new Http1Connection(ctx,
                                   recvListener,
                                   sendListener,
                                   maxPrologueLength,
                                   maxHeadersSize,
                                   validateHeaders,
                                   validatePath,
                                   upgradeProviderMap);
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.webserver.http1.Http1ConnectionProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, Http1ConnectionProvider> {

        private final List<Http1ConnectionListener> sendListeners = new LinkedList<>();
        private final List<Http1ConnectionListener> recvListeners = new LinkedList<>();
        private final HelidonServiceLoader.Builder<Http1UpgradeProvider> upgradeProviders =
                HelidonServiceLoader.builder(ServiceLoader.load(Http1UpgradeProvider.class));

        private int maxPrologueLength = DEFAULT_MAX_PROLOGUE_LENGTH;
        private int maxHeaderSize = DEFAULT_MAX_HEADERS_SIZE;
        private boolean validateHeaders = DEFAULT_VALIDATE_HEADERS;
        private boolean validatePath = DEFAULT_VALIDATE_PATH;

        private Builder() {
            Config config = Config.create()
                    .get("nima.server.connection-providers.http_1_1");

            config.get("validate-headers").asBoolean().ifPresent(this::validateHeaders);
            config.get("validate-path").asBoolean().ifPresent(this::validatePath);
            if (config.get("recv-log").asBoolean().orElse(true)) {
                addSendListener(new Http1LoggingConnectionListener("send"));
            }
            if (config.get("send-log").asBoolean().orElse(true)) {
                addReceiveListener(new Http1LoggingConnectionListener("recv"));
            }
        }

        @Override
        public Http1ConnectionProvider build() {
            return new Http1ConnectionProvider(this);
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
         * @param maxHeadersSize maximal header size
         * @return updated builder
         */
        public Builder maxHeadersSize(int maxHeadersSize) {
            this.maxHeaderSize = maxHeadersSize;
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
         * Add a configured upgrade provider. This will replace the instance discovered through service loader (if one exists).
         *
         * @param provider add a provider
         * @return updated builder
         */
        public Builder addUpgradeProvider(Http1UpgradeProvider provider) {
            upgradeProviders.addService(provider);
            return this;
        }

        /**
         * Add a send listener.
         *
         * @param listener listener to add
         * @return updated builder
         */
        public Builder addSendListener(Http1ConnectionListener listener) {
            sendListeners.add(listener);
            return this;
        }

        /**
         * Add a receive listener.
         *
         * @param listener listener to add
         * @return updated builder
         */
        public Builder addReceiveListener(Http1ConnectionListener listener) {
            recvListeners.add(listener);
            return this;
        }

        Http1ConnectionListener sendListener() {
            return Http1ConnectionListener.create(sendListeners);
        }

        Http1ConnectionListener recvListener() {
            return Http1ConnectionListener.create(recvListeners);
        }

        Map<String, Http1UpgradeProvider> upgradeProviders() {
            List<Http1UpgradeProvider> providers = upgradeProviders.build().asList();
            Map<String, Http1UpgradeProvider> providerMap = new HashMap<>();

            for (Http1UpgradeProvider upgradeProvider : providers) {
                providerMap.put(upgradeProvider.supportedProtocol(), upgradeProvider);
            }
            return Map.copyOf(providerMap);
        }
    }
}
