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

package io.helidon.security.providers.oidc.common;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.ws.rs.client.ClientBuilder;

import io.helidon.common.Errors;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.webclient.Proxy;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.tracing.WebClientTracing;

import org.glassfish.jersey.client.ClientProperties;

final class OidcUtil {
    private static final Logger LOGGER = Logger.getLogger(OidcUtil.class.getName());

    private OidcUtil() {
    }

    static String fixServerType(String serverType) {
        if (serverType != null) {
            // explicit server type
            if (!"idcs".equals(serverType) && !OidcConfig.Builder.DEFAULT_SERVER_TYPE.equals(serverType)) {
                LOGGER.warning("OIDC server-type is configured to " + serverType + ", currently only \"idcs\", and"
                                       + " \"" + OidcConfig.Builder.DEFAULT_SERVER_TYPE + "\" are supported");
                return OidcConfig.Builder.DEFAULT_SERVER_TYPE;
            }
        } else {
            return OidcConfig.Builder.DEFAULT_SERVER_TYPE;
        }
        return serverType;
    }

    static ClientBuilder clientBaseBuilder(String proxyProtocol, String proxyHost, int proxyPort) {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();

        clientBuilder.property(OutboundConfig.PROPERTY_DISABLE_OUTBOUND, Boolean.TRUE);

        if (proxyHost != null) {
            clientBuilder.property(ClientProperties.PROXY_URI, proxyProtocol
                    + "://"
                    + proxyHost
                    + ":"
                    + proxyPort);
        }

        return clientBuilder;
    }

    static WebClient.Builder webClientBaseBuilder(String proxyProtocol,
                                                  String proxyHost,
                                                  int proxyPort,
                                                  Duration clientTimeout) {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .addService(WebClientTracing.create())
                .addMediaSupport(JsonpSupport.create())
                .connectTimeout(clientTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(clientTimeout.toMillis(), TimeUnit.MILLISECONDS);

        if (proxyHost != null) {
            webClientBuilder.proxy(Proxy.builder()
                                           .type(Proxy.ProxyType.HTTP)
                                           .host(proxyProtocol + "://" + proxyHost)
                                           .port(proxyPort)
                                           .build());
        }
        return webClientBuilder;
    }

    static void validateExists(Errors.Collector collector, Object value, String name, String configKey) {
        // validate
        if (value == null) {
            collector.fatal(name + " must be configured (\"" + configKey + "\" key in config)");
        }
    }
}
