/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.http3;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.Api;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.Junit5Util;
import io.helidon.webserver.testing.junit5.spi.ServerJunitExtension;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * A {@link ServiceLoader} provider implementation that adds support for injection of HTTP/3 related
 * artifacts, such as {@link Http3Client} and {@link Http3LowLevelClient} in Helidon integration tests.
 */
@Api.Internal
public class Http3ServerExtension implements ServerJunitExtension {
    private final Map<Parameter, Http3Client> clients = new ConcurrentHashMap<>();
    private final Map<Parameter, Http3LowLevelClient> lowLevelClients = new ConcurrentHashMap<>();
    private final Map<String, ListenerConfig.Builder> listenerBuilders = new ConcurrentHashMap<>();

    /**
     * Required constructor for {@link ServiceLoader}.
     */
    public Http3ServerExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        listenerBuilders.clear();
        lowLevelClients.clear();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> parameterType = parameterContext.getParameter().getType();
        return Http3Client.class.equals(parameterType) || Http3LowLevelClient.class.equals(parameterType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext,
                                   Class<?> parameterType,
                                   WebServer server) {
        if (!Http3Client.class.equals(parameterType)) {
            if (!Http3LowLevelClient.class.equals(parameterType)) {
                throw new ParameterResolutionException("HTTP/3 extension only supports Http3Client and "
                                                               + "Http3LowLevelClient parameter types");
            }
        }

        Parameter parameter = parameterContext.getParameter();
        String socketName = Junit5Util.socketName(parameter);
        int port = server.port(socketName);
        if (port < 0) {
            throw new ParameterResolutionException("Socket " + socketName + " is not available for HTTP/3 client injection");
        }
        if (!server.hasTls(socketName)) {
            throw new ParameterResolutionException("Socket " + socketName + " does not have TLS enabled, which HTTP/3 "
                                                          + "requires");
        }

        URI baseUri = URI.create("https://localhost:" + port + "/");
        Tls clientTls = clientTls(parameterContext, extensionContext, server, socketName);
        if (Http3Client.class.equals(parameterType)) {
            return clients.computeIfAbsent(parameter, ignored -> Http3Client.builder()
                    .baseUri(baseUri)
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(clientTls)
                    .protocolConfig(Http3ClientProtocolConfig.builder()
                                            .priorKnowledge(true)
                                            .build())
                    .build());
        }
        return lowLevelClients.computeIfAbsent(parameter, ignored -> Http3LowLevelClient.create(baseUri, clientTls));
    }

    @Override
    public void updateListenerBuilder(String socketName,
                                      ListenerConfig.Builder listenerBuilder,
                                      Router.RouterBuilder<?> routerBuilder) {
        listenerBuilders.put(socketName, listenerBuilder);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        clients.values().forEach(Http3Client::closeResource);
        clients.clear();
        lowLevelClients.values().forEach(Http3LowLevelClient::close);
        lowLevelClients.clear();
    }

    private static List<X509Certificate> clientCertificates(ParameterContext parameterContext,
                                                            ExtensionContext extensionContext,
                                                            Tls listenerTls) {
        Http3ClientTls configuredTls = configuredTls(parameterContext, extensionContext);
        if (configuredTls != null) {
            return annotatedClientCertificates(configuredTls, extensionContext);
        }

        List<X509Certificate> certificates = listenerTls.prototype().privateKeyCertChain();
        if (certificates.isEmpty()) {
            throw new IllegalStateException("HTTP/3 client injection requires @Http3ClientTls on the parameter"
                                                    + " or test class when listener certificates are not available");
        }
        return certificates;
    }

    private static List<X509Certificate> annotatedClientCertificates(Http3ClientTls configuredTls,
                                                                     ExtensionContext extensionContext) {
        try {
            ClassLoader classLoader = extensionContext.getRequiredTestClass().getClassLoader();
            KeyStore configuredStore = KeyStore.getInstance(configuredTls.type());
            try (InputStream stream = classLoader.getResourceAsStream(configuredTls.resource())) {
                if (stream == null) {
                    throw new IllegalStateException("Missing HTTP/3 client TLS resource: " + configuredTls.resource());
                }
                configuredStore.load(stream, configuredTls.passphrase().toCharArray());
            }

            List<X509Certificate> certificates = new ArrayList<>();
            Enumeration<String> aliases = configuredStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate certificate = configuredStore.getCertificate(alias);
                if (certificate instanceof X509Certificate x509Certificate) {
                    certificates.add(x509Certificate);
                }
                Certificate[] chain = configuredStore.getCertificateChain(alias);
                if (chain != null) {
                    for (Certificate chained : chain) {
                        if (chained instanceof X509Certificate x509Certificate) {
                            certificates.add(x509Certificate);
                        }
                    }
                }
            }
            if (certificates.isEmpty()) {
                throw new IllegalStateException("HTTP/3 client TLS resource does not contain X.509 certificates: "
                                                        + configuredTls.resource());
            }
            return certificates;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to load HTTP/3 test client TLS resource: "
                                                    + configuredTls.resource(), e);
        }
    }

    private static Http3ClientTls configuredTls(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.findAnnotation(Http3ClientTls.class)
                .orElseGet(() -> extensionContext.getRequiredTestClass().getAnnotation(Http3ClientTls.class));
    }

    private Tls clientTls(ParameterContext parameterContext,
                          ExtensionContext extensionContext,
                          WebServer server,
                          String socketName) {
        Tls listenerTls = listenerTls(server, socketName);

        return Tls.builder()
                .trust(clientCertificates(parameterContext, extensionContext, listenerTls))
                .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                .enabledProtocols(List.of("TLSv1.3"))
                .build();
    }

    private Tls listenerTls(WebServer server, String socketName) {
        ListenerConfig.Builder listenerBuilder = listenerBuilders.get(socketName);
        if (listenerBuilder != null) {
            return listenerBuilder.tls()
                    .orElseThrow(() -> new IllegalStateException("Socket " + socketName + " does not have TLS configured"));
        }

        WebServerConfig config = server.prototype();
        if (WebServer.DEFAULT_SOCKET_NAME.equals(socketName)) {
            return config.tls()
                    .orElseThrow(() -> new IllegalStateException("Default socket does not have TLS configured"));
        }

        ListenerConfig listenerConfig = config.sockets().get(socketName);
        if (listenerConfig == null) {
            throw new IllegalStateException("Socket " + socketName + " is not available for HTTP/3 client injection");
        }

        return listenerConfig.tls()
                .orElseThrow(() -> new IllegalStateException("Socket " + socketName + " does not have TLS configured"));
    }
}
