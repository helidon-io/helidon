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

package io.helidon.nima.testing.junit5.webserver;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.nima.testing.junit5.webserver.spi.ServerJunitExtension;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.ListenerConfiguration;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * Java {@link java.util.ServiceLoader} provider implementation of
 * a {@link io.helidon.nima.testing.junit5.webserver.spi.ServerJunitExtension} that adds support for HTTP/1.1.
 */
public class Http1ServerJunitExtension implements ServerJunitExtension {
    private final Map<String, SocketHttpClient> socketHttpClients = new ConcurrentHashMap<>();
    private final Map<String, Http1Client> httpClients = new ConcurrentHashMap<>();

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

        throw new ParameterResolutionException("Parameter of type " + parameterType.getName() + " not supported");
    }

    @Override
    public Optional<ParamHandler<?>> setUpRouteParamHandler(Class<?> type) {

        if (ListenerConfiguration.Builder.class.equals(type)) {
            return Optional.of(new ListenerConfigurationParamHandler());
        } else if (Router.RouterBuilder.class.equals(type)) {
            return Optional.of(new RouterParamHandler());
        } else if (HttpRules.class.equals(type) || HttpRouting.Builder.class.equals(type)) {
            return Optional.of(new RoutingParamHandler());
        }

        return Optional.empty();
    }

    private Http1Client httpClient(WebServer server, String socketName) {
        return WebClient.builder()
                .baseUri("http://localhost:" + server.port(socketName))
                .build();
    }

    private SocketHttpClient socketHttpClient(WebServer server, String socketName) {
        return SocketHttpClient.create(server.port(socketName));
    }

    private static class RoutingParamHandler implements ParamHandler<HttpRouting.Builder> {
        @Override
        public HttpRouting.Builder get(String socketName,
                                       WebServer.Builder serverBuilder,
                                       ListenerConfiguration.Builder listenerBuilder,
                                       Router.RouterBuilder<?> routerBuilder) {
            return HttpRouting.builder();
        }

        @Override
        public void handle(String socketName,
                           WebServer.Builder serverBuilder,
                           ListenerConfiguration.Builder listenerBuilder,
                           Router.RouterBuilder<?> routerBuilder,
                           HttpRouting.Builder value) {

            routerBuilder.addRouting(value.build());
        }
    }

    private static class RouterParamHandler implements ParamHandler<Router.RouterBuilder<?>> {
        @Override
        public Router.RouterBuilder<?> get(String socketName,
                                           WebServer.Builder serverBuilder,
                                           ListenerConfiguration.Builder listenerBuilder,
                                           Router.RouterBuilder<?> routerBuilder) {
            return routerBuilder;
        }
    }

    private static class ListenerConfigurationParamHandler implements ParamHandler<ListenerConfiguration.Builder> {
        @Override
        public ListenerConfiguration.Builder get(String socketName,
                                                 WebServer.Builder serverBuilder,
                                                 ListenerConfiguration.Builder listenerBuilder,
                                                 Router.RouterBuilder<?> routerBuilder) {
            return listenerBuilder;
        }
    }
}
