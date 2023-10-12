/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.util.List;
import java.util.Map;

import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.webclient.api.WebClient;

/**
 * Configuration options specific to the Client API that utilizes {@link HelidonConnector}.
 */
public final class HelidonProperties {

    private HelidonProperties() {
    }

    /**
     * Property name to set a {@link Config} instance to by used by underlying {@link WebClient}.
     * This property is settable on {@link jakarta.ws.rs.core.Configurable#property(String, Object)}.
     *
     * @see io.helidon.webclient.api.WebClientConfig.Builder#config(io.helidon.common.config.Config)
     */
    public static final String CONFIG = "jersey.connector.helidon.config";

    /**
     * Property name to set a {@link Tls} instance to be used by underlying {@link WebClient}.
     * This property is settable on {@link jakarta.ws.rs.core.Configurable#property(String, Object)}.
     *
     * @see io.helidon.webclient.api.WebClientConfig.Builder#tls(Tls)
     */
    public static final String TLS = "jersey.connector.helidon.tls";

    /**
     * Property name to set a {@code List<? extends  ProtocolConfig>} instance with a list of
     * protocol configs to be used by underlying {@link WebClient}.
     * This property is settable on {@link jakarta.ws.rs.core.Configurable#property(String, Object)}.
     *
     * @see io.helidon.webclient.api.WebClientConfig.Builder#protocolConfigs(List)
     */
    public static final String PROTOCOL_CONFIGS = "jersey.connector.helidon.protocolConfigs";

    /**
     * Property name to set a {@code Map<String, String>} instance with a list of
     * default headers to be used by underlying {@link WebClient}.
     * This property is settable on {@link jakarta.ws.rs.core.Configurable#property(String, Object)}.
     *
     * @see io.helidon.webclient.api.WebClientConfig.Builder#defaultHeadersMap(Map)
     */
    public static final String DEFAULT_HEADERS = "jersey.connector.helidon.defaultHeaders";

    /**
     * Property name to set a protocol ID for each request. You can use this property
     * to request an HTTP/2 upgrade from HTTP/1.1 by setting its value to {@code "h2"}.
     * When using TLS, Helidon uses negotiation via the ALPN extension instead of this
     * property.
     *
     * @see io.helidon.webclient.api.HttpClientRequest#protocolId(String)
     */
    public static final String PROTOCOL_ID = "jersey.connector.helidon.protocolId";

    /**
     * Property name to enable or disable connection caching in the underlying {@link WebClient}.
     * The default for the Helidon connector is {@code false}, or no sharing (which is the
     * opposite of {@link WebClient}). Set this property to {@code true} to enable connection
     * caching.
     *
     * @see io.helidon.webclient.api.WebClientConfig.Builder#shareConnectionCache(boolean)
     */
    public static final String SHARE_CONNECTION_CACHE = "jersey.connector.helidon.shareConnectionCache";
}
