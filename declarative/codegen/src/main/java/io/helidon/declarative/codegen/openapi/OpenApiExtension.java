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

package io.helidon.declarative.codegen.openapi;

import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.http.webserver.ServerEndpointAnalyzer;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_ENDPOINT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTENSIONS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTENSION_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_EXTERNAL_DOCS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_HIDDEN_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_OPERATION_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_PARAMETERS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_PARAMETER_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_REQUEST_BODY_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_RESPONSES_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_RESPONSE_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SERVERS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SERVER_ANNOTATION;

final class OpenApiExtension implements RegistryCodegenExtension {
    private static final Set<TypeName> TYPE_ANNOTATIONS = Set.of(OPENAPI_DOCUMENT_ANNOTATION,
                                                                 OPENAPI_ENDPOINT_ANNOTATION,
                                                                 OPENAPI_HIDDEN_ANNOTATION,
                                                                 OPENAPI_SECURITY_REQUIREMENT_ANNOTATION,
                                                                 OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION,
                                                                 OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION);
    private static final Set<TypeName> METHOD_ANNOTATIONS = Set.of(OPENAPI_SERVER_ANNOTATION,
                                                                   OPENAPI_SERVERS_ANNOTATION,
                                                                   OPENAPI_EXTERNAL_DOCS_ANNOTATION,
                                                                   OPENAPI_EXTENSION_ANNOTATION,
                                                                   OPENAPI_EXTENSIONS_ANNOTATION,
                                                                   OPENAPI_SECURITY_REQUIREMENT_ANNOTATION,
                                                                   OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION,
                                                                   OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION,
                                                                   OPENAPI_OPERATION_ANNOTATION,
                                                                   OPENAPI_PARAMETER_ANNOTATION,
                                                                   OPENAPI_PARAMETERS_ANNOTATION,
                                                                   OPENAPI_REQUEST_BODY_ANNOTATION,
                                                                   OPENAPI_RESPONSE_ANNOTATION,
                                                                   OPENAPI_RESPONSES_ANNOTATION,
                                                                   OPENAPI_HIDDEN_ANNOTATION);
    private static final Set<TypeName> PARAMETER_ANNOTATIONS = Set.of(OPENAPI_PARAMETER_ANNOTATION,
                                                                      OPENAPI_PARAMETERS_ANNOTATION);

    private final RegistryCodegenContext ctx;
    private final ServerEndpointAnalyzer endpointAnalyzer;
    private final OpenApiSourceGenerator sourceGenerator;

    OpenApiExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
        this.endpointAnalyzer = ServerEndpointAnalyzer.create(ctx);
        this.sourceGenerator = new OpenApiSourceGenerator(ctx);
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        sourceGenerator.processDocuments(roundContext);
        var endpointTypes = roundContext.types()
                .stream()
                .map(type -> ctx.typeInfo(type.typeName()).orElse(type))
                .toList();
        var endpoints = endpointAnalyzer.endpoints(endpointTypes)
                .stream()
                .filter(endpoint -> hasAny(endpoint.annotations(), TYPE_ANNOTATIONS)
                        || endpoint.methods()
                        .stream()
                        .anyMatch(method -> hasAny(method.annotations(), METHOD_ANNOTATIONS)
                                || method.parameters()
                                .stream()
                                .anyMatch(parameter -> hasAny(parameter.annotations(), PARAMETER_ANNOTATIONS))))
                .toList();
        sourceGenerator.processEndpoints(roundContext, endpoints);
    }

    private static boolean hasAny(Set<Annotation> annotations, Set<TypeName> types) {
        return annotations.stream()
                .map(Annotation::typeName)
                .anyMatch(types::contains);
    }
}
