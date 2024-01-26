/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.grpc;

import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.Junit5Util;
import io.helidon.webserver.testing.junit5.spi.ServerJunitExtension;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * A {@link java.util.ServiceLoader} provider implementation that adds support for injection of gRPC related
 * artifacts, such as {@link io.helidon.webclient.grpc.GrpcClient} in Helidon integration tests.
 */
public class GrpcServerExtension implements ServerJunitExtension {
    /**
     * Required constructor for {@link java.util.ServiceLoader}.
     */
    public GrpcServerExtension() {
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return GrpcClient.class.equals(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext,
                                   Class<?> parameterType,
                                   WebServer server) {
        String socketName = Junit5Util.socketName(parameterContext.getParameter());

        if (GrpcClient.class.equals(parameterType)) {
            return GrpcClient.builder()
                    .baseUri("http://localhost:" + server.port(socketName))
                    .build();
        }
        throw new ParameterResolutionException("gRPC extension only supports GrpcClient parameter type");
    }
}
