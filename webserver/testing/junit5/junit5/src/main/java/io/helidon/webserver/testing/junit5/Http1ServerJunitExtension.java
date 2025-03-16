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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.service.registry.Service;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.spi.ServerJunitExtension;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * Java {@link java.util.ServiceLoader} provider implementation of
 * a {@link io.helidon.webserver.testing.junit5.spi.ServerJunitExtension} that adds support for HTTP/1.1.
 */
public class Http1ServerJunitExtension implements ServerJunitExtension {
    private final Map<String, SocketHttpClient> socketHttpClients = new ConcurrentHashMap<>();
    private final Map<String, Http1Client> httpClients = new ConcurrentHashMap<>();
    private final Map<String, WebClient> webClients = new ConcurrentHashMap<>();

    /**
     * Public constructor as required by {@link java.util.ServiceLoader}.
     */
    public Http1ServerJunitExtension() {
    }

    @Override
    public void afterEach(ExtensionContext context) {
        socketHttpClients.values().forEach(SocketHttpClient::disconnect);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(Http1Client.class)) {
            return true;
        }
        if (paramType.equals(SocketHttpClient.class)) {
            return true;
        }
        if (paramType.equals(WebClient.class)) {
            return true;
        }

        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext,
                                   Class<?> parameterType,
                                   WebServer server) {

        if (parameterType.equals(SocketHttpClient.class)) {
            return socketHttpClients.computeIfAbsent(Junit5Util.socketName(parameterContext.getParameter()),
                                                     it -> socketHttpClient(server, it));
        }
        if (parameterType.equals(Http1Client.class)) {
            return httpClients.computeIfAbsent(Junit5Util.socketName(parameterContext.getParameter()),
                                               it -> httpClient(server, it));
        }
        if (parameterType.equals(WebClient.class)) {
            return webClients.computeIfAbsent(Junit5Util.socketName(parameterContext.getParameter()),
                                              it -> webClient(server, it));
        }

        throw new ParameterResolutionException("Parameter of type " + parameterType.getName() + " not supported");
    }

    @Override
    public Optional<ParamHandler<?>> setUpRouteParamHandler(Class<?> type) {

        if (ListenerConfig.Builder.class.equals(type)) {
            return Optional.of(new ListenerConfigurationParamHandler());
        } else if (Router.RouterBuilder.class.equals(type)) {
            return Optional.of(new RouterParamHandler());
        } else if (HttpRules.class.equals(type) || HttpRouting.Builder.class.equals(type)) {
            return Optional.of(new RoutingParamHandler());
        }

        return Optional.empty();
    }

    private WebClient webClient(WebServer server, String socketName) {
        return WebClient.builder()
                .baseUri("http://localhost:" + server.port(socketName))
                .build();
    }

    private Http1Client httpClient(WebServer server, String socketName) {
        return Http1Client.builder()
                .baseUri("http://localhost:" + server.port(socketName))
                .build();
    }

    private SocketHttpClient socketHttpClient(WebServer server, String socketName) {
        return SocketHttpClient.create(server.port(socketName));
    }

    private static class RoutingParamHandler implements ParamHandler<HttpRouting.Builder> {
        @Override
        public HttpRouting.Builder get(String socketName,
                                       WebServerConfig.Builder serverBuilder,
                                       ListenerConfig.Builder listenerBuilder,
                                       Router.RouterBuilder<?> routerBuilder) {
            if (listenerBuilder.routing().isEmpty()) {
                listenerBuilder.routing(HttpRouting.builder());
            }
            return listenerBuilder.routing().get();
        }

        @Override
        public void handle(String socketName,
                           WebServerConfig.Builder serverBuilder,
                           ListenerConfig.Builder listenerBuilder,
                           Router.RouterBuilder<?> routerBuilder,
                           HttpRouting.Builder value) {

            routerBuilder.addRouting(value);
        }
    }

    private static class RouterParamHandler implements ParamHandler<Router.RouterBuilder<?>> {
        @Override
        public Router.RouterBuilder<?> get(String socketName,
                                           WebServerConfig.Builder serverBuilder,
                                           ListenerConfig.Builder listenerBuilder,
                                           Router.RouterBuilder<?> routerBuilder) {
            return routerBuilder;
        }
    }

    private static class ListenerConfigurationParamHandler implements ParamHandler<ListenerConfig.Builder> {
        @Override
        public ListenerConfig.Builder get(String socketName,
                                          WebServerConfig.Builder serverBuilder,
                                          ListenerConfig.Builder listenerBuilder,
                                          Router.RouterBuilder<?> routerBuilder) {
            return listenerBuilder;
        }
    }
}
