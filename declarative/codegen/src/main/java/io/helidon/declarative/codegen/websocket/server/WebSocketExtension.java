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

package io.helidon.declarative.codegen.websocket.server;

import java.util.Collection;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.websocket.server.WebSocketServerTypes.ANNOTATION_ENDPOINT;

class WebSocketExtension implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(WebSocketExtension.class);

    private final RegistryCodegenContext ctx;

    WebSocketExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        // process all `WebSocketServer.Endpoint` cases
        Collection<TypeInfo> serverEndpoints = roundContext.annotatedTypes(ANNOTATION_ENDPOINT);

        for (TypeInfo serverEndpoint : serverEndpoints) {
            process(roundContext, serverEndpoint);
        }
    }

    private void process(RegistryRoundContext roundContext, TypeInfo serverEndpoint) {
        if (serverEndpoint.kind() == ElementKind.INTERFACE) {
            throw new CodegenException("Interfaces should not be annotated with " + ANNOTATION_ENDPOINT.fqName(),
                                       serverEndpoint.originatingElementValue());
        }

        TypeName endpointType = serverEndpoint.typeName();
        // we need to generate two types - WsRouteRegistration and WsListener
        String classNameBase = endpointType.classNameWithEnclosingNames().replace('.', '_');

        String listenerClassName = classNameBase + "__WsListener";
        String registrationClassName = classNameBase + "__WsRegistration";

        TypeName generatedListener = TypeName.builder()
                .packageName(endpointType.packageName())
                .className(listenerClassName)
                .build();
        TypeName generatedRegistration = TypeName.builder()
                .packageName(endpointType.packageName())
                .className(registrationClassName)
                .build();

        WebSocketRegistrationGenerator.generate(roundContext,
                                                serverEndpoint,
                                                endpointType,
                                                generatedRegistration,
                                                generatedListener);
        WebSocketListenerGenerator.generate(roundContext,
                                            serverEndpoint,
                                            endpointType,
                                            generatedListener);
    }
}
