/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.http2;

import java.net.URI;
import java.util.Set;

import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.Junit5Util;
import io.helidon.webserver.testing.junit5.spi.ServerJunitExtension;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * A {@link java.util.ServiceLoader} provider implementation that adds support for injection of HTTP/2 related
 * artifacts, such as {@link io.helidon.webclient.http2.Http2Client} in Helidon integration tests.
 */
public class Http2ServerExtension implements ServerJunitExtension {

    private static final Set<Class<?>> SUPPORTED = Set.of(Http2Client.class, Http2TestClient.class);

    /**
     * Required constructor for {@link java.util.ServiceLoader}.
     */
    public Http2ServerExtension() {
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext,
                                   Class<?> parameterType,
                                   WebServer server) {
        String socketName = Junit5Util.socketName(parameterContext.getParameter());

        URI uri = URI.create("http://localhost:" + server.port(socketName));

        if (Http2Client.class.equals(parameterType)) {
            return Http2Client.builder()
                    .baseUri(uri)
                    .build();
        }

        if (Http2TestClient.class.equals(parameterType)) {
            Http2TestClient client = new Http2TestClient(uri);
            extensionContext
                    .getStore(ExtensionContext.Namespace.GLOBAL)
                    .put(Http2TestClient.class.getName(), client);
            return client;
        }

        throw new ParameterResolutionException("HTTP/2 extension only supports Http2Client parameter type");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ServerJunitExtension.super.afterEach(context);
        Http2TestClient client = (Http2TestClient) context
                .getStore(ExtensionContext.Namespace.GLOBAL)
                .remove(Http2TestClient.class.getName());
        if (client != null) {
            client.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return SUPPORTED.contains(parameterContext.getParameter().getType());
    }
}
