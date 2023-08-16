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

package io.helidon.webserver.testing.junit5;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.spi.DirectJunitExtension;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * A Java {@link java.util.ServiceLoader} provider implementation of
 * {@link io.helidon.webserver.testing.junit5.spi.DirectJunitExtension} for HTTP/1.1 tests.
 */
public class Http1DirectJunitExtension implements DirectJunitExtension {
    private final Map<String, DirectClient> clients = new HashMap<>();
    private final Map<String, DirectWebClient> webClients = new HashMap<>();

    @Override
    public void afterAll(ExtensionContext context) {
        clients.values().forEach(DirectClient::close);
        webClients.values().forEach(DirectWebClient::close);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        clients.values()
                .forEach(client -> client.clientTlsPrincipal(null)
                        .clientTlsCertificates(null)
                        .clientHost("helidon-unit")
                        .clientPort(65000)
                        .serverHost("helidon-unit-server")
                        .serverPort(8080)
                );
        webClients.values()
                .forEach(client -> client.clientTlsPrincipal(null)
                        .clientTlsCertificates(null)
                        .clientHost("helidon-unit")
                        .clientPort(65000)
                        .serverHost("helidon-unit-server")
                        .serverPort(8080)
                );
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (DirectClient.class.equals(paramType)) {
            return true;
        }
        if (Http1Client.class.equals(paramType)) {
            return true;
        }
        if (WebClient.class.equals(paramType)) {
            return true;
        }

        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext, Class<?> paramType) {
        if (DirectClient.class.equals(paramType) || Http1Client.class.equals(paramType)) {
            String socketName = Junit5Util.socketName(parameterContext.getParameter());

            DirectClient directClient = clients.get(socketName);

            if (directClient == null) {
                if (WebServer.DEFAULT_SOCKET_NAME.equals(socketName)) {
                    throw new IllegalStateException("There is no default routing specified. Please add static method "
                                                            + "annotated with @SetUpRoute that accepts HttpRouting.Builder,"
                                                            + " or HttpRules");
                } else {
                    throw new IllegalStateException("There is no default routing specified for socket \"" + socketName + "\"."
                                                            + " Please add static method "
                                                            + "annotated with @SetUpRoute that accepts HttpRouting.Builder,"
                                                            + " or HttpRules, and add @Socket(\"" + socketName + "\") "
                                                            + "annotation to the parameter");
                }
            }
            return directClient;
        }
        if (WebClient.class.equals(paramType)) {
            String socketName = Junit5Util.socketName(parameterContext.getParameter());
            WebClient directClient = webClients.get(socketName);

            if (directClient == null) {
                if (WebServer.DEFAULT_SOCKET_NAME.equals(socketName)) {
                    throw new IllegalStateException("There is no default routing specified. Please add static method "
                                                            + "annotated with @SetUpRoute that accepts HttpRouting.Builder,"
                                                            + " or HttpRules");
                } else {
                    throw new IllegalStateException("There is no default routing specified for socket \"" + socketName + "\"."
                                                            + " Please add static method "
                                                            + "annotated with @SetUpRoute that accepts HttpRouting.Builder,"
                                                            + " or HttpRules, and add @Socket(\"" + socketName + "\") "
                                                            + "annotation to the parameter");
                }
            }
            return directClient;
        }

        throw new ParameterResolutionException("Cannot resolve parameter: " + parameterContext);
    }

    @Override
    public Optional<ParamHandler<?>> setUpRouteParamHandler(Class<?> type) {
        if (HttpRouting.Builder.class.equals(type) || HttpRules.class.equals(type)) {
            return Optional.of(new RoutingParamHandler(clients, webClients));
        }
        return Optional.empty();
    }

    private static final class RoutingParamHandler implements DirectJunitExtension.ParamHandler<HttpRouting.Builder> {
        private final Map<String, DirectClient> clients;
        private final Map<String, DirectWebClient> webClients;

        private RoutingParamHandler(Map<String, DirectClient> clients, Map<String, DirectWebClient> webClients) {
            this.clients = clients;
            this.webClients = webClients;
        }

        @Override
        public HttpRouting.Builder get(String socketName) {
            return HttpRouting.builder();
        }

        @Override
        public void handle(Method method, String socketName, HttpRouting.Builder value) {
            HttpRouting routing = value.build();

            if (clients.putIfAbsent(socketName, new DirectClient(routing)) != null) {
                throw new IllegalStateException("Method "
                                                        + method
                                                        + " defines HTTP routing for socket \""
                                                        + socketName
                                                        + "\""
                                                        + " that is already defined for class \""
                                                        + method.getDeclaringClass().getName()
                                                        + "\".");
            }

            if (webClients.putIfAbsent(socketName, new DirectWebClient(routing)) != null) {
                throw new IllegalStateException("Method "
                                                        + method
                                                        + " defines HTTP routing for socket \""
                                                        + socketName
                                                        + "\""
                                                        + " that is already defined for class \""
                                                        + method.getDeclaringClass().getName()
                                                        + "\".");
            }
        }
    }
}
