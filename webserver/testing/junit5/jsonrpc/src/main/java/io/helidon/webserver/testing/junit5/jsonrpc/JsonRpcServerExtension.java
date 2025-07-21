/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.jsonrpc;

import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.Junit5Util;
import io.helidon.webserver.testing.junit5.spi.ServerJunitExtension;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * A {@link java.util.ServiceLoader} provider implementation that adds support for
 * injection of JSON-RPC related artifacts, such as
 * {@link io.helidon.webclient.jsonrpc.JsonRpcClient} in Helidon integration tests.
 */
public class JsonRpcServerExtension implements ServerJunitExtension {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return JsonRpcClient.class.equals(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext,
                                   Class<?> parameterType,
                                   WebServer server) {
        String socketName = Junit5Util.socketName(parameterContext.getParameter());

        if (JsonRpcClient.class.equals(parameterType)) {
            return JsonRpcClient.builder()
                    .baseUri("http://localhost:" + server.port(socketName))
                    .build();
        }
        throw new ParameterResolutionException("JSON-RPC extension only supports JsonRpcClient parameter type");
    }
}
