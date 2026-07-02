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

package io.helidon.declarative.codegen.http.webserver;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeUtils;
import io.helidon.declarative.codegen.http.HttpCodegenValidation;
import io.helidon.declarative.codegen.http.RestExtensionBase;
import io.helidon.declarative.codegen.model.http.HttpStatus;
import io.helidon.declarative.codegen.model.http.RestMethod;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;
import io.helidon.declarative.codegen.model.http.ServerEndpoint;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_ENTITY_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_METHOD_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_QUERY_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.webserver.WebServerCodegenTypes.REST_SERVER_COMPUTED_HEADER;
import static io.helidon.declarative.codegen.http.webserver.WebServerCodegenTypes.REST_SERVER_COMPUTED_HEADERS;
import static io.helidon.declarative.codegen.http.webserver.WebServerCodegenTypes.REST_SERVER_ENDPOINT;
import static io.helidon.declarative.codegen.http.webserver.WebServerCodegenTypes.REST_SERVER_HEADER;
import static io.helidon.declarative.codegen.http.webserver.WebServerCodegenTypes.REST_SERVER_HEADERS;
import static io.helidon.declarative.codegen.http.webserver.WebServerCodegenTypes.REST_SERVER_LISTENER;
import static io.helidon.declarative.codegen.http.webserver.WebServerCodegenTypes.REST_SERVER_STATUS;
import static java.util.function.Predicate.not;

/**
 * Analyzer for declarative WebServer endpoints.
 */
final class ServerEndpointAnalyzer extends RestExtensionBase {
    private final RegistryCodegenContext ctx;

    ServerEndpointAnalyzer(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Analyze endpoint types available in the provided round.
     *
     * @param roundContext codegen round context
     * @return analyzed endpoints
     */
    List<ServerEndpoint> endpoints(RegistryRoundContext roundContext) {
        return roundContext.annotatedTypes(REST_SERVER_ENDPOINT)
                .stream()
                .map(this::endpoint)
                .toList();
    }

    /**
     * Analyze a single endpoint type.
     *
     * @param typeInfo endpoint type
     * @return analyzed endpoint
     */
    ServerEndpoint endpoint(TypeInfo typeInfo) {
        var builder = ServerEndpoint.builder()
                .type(typeInfo);

        Set<Annotation> typeAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
        builder.annotations(typeAnnotations);

        Annotations.findFirst(REST_SERVER_LISTENER, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .ifPresent(builder::listener);
        builder.listenerRequired(true);

        path(typeAnnotations, builder);
        produces(typeAnnotations, builder);
        consumes(typeAnnotations, builder);
        headers(typeAnnotations, builder, REST_SERVER_HEADERS, REST_SERVER_HEADER);
        computedHeaders(typeAnnotations, builder, REST_SERVER_COMPUTED_HEADERS, REST_SERVER_COMPUTED_HEADER);

        typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .forEach(it -> method(typeInfo, builder, it));

        return builder.build();
    }

    private void method(TypeInfo endpoint,
                        ServerEndpoint.Builder endpointBuilder,
                        TypedElementInfo method) {
        Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, endpoint, method));

        Optional<Annotation> httpMethodAnnotation = DeclarativeUtils.findMetaAnnotated(annotations, HTTP_METHOD_ANNOTATION);
        if (httpMethodAnnotation.isEmpty()) {
            return;
        }

        String methodName = method.elementName();
        String uniqueName = ctx.uniqueName(endpoint, method);

        var builder = RestMethod.builder()
                .returnType(method.typeName())
                .type(endpoint)
                .name(methodName)
                .uniqueName(uniqueName)
                .method(method)
                .annotations(annotations)
                .httpMethod(httpMethodFromAnnotation(method, httpMethodAnnotation.get()));

        path(annotations, builder);
        consumes(annotations, builder);
        produces(annotations, builder);
        headers(annotations, builder, REST_SERVER_HEADERS, REST_SERVER_HEADER);
        computedHeaders(annotations, builder, REST_SERVER_COMPUTED_HEADERS, REST_SERVER_COMPUTED_HEADER);

        if (builder.consumes().isEmpty()) {
            builder.consumes(endpointBuilder.consumes());
        }
        if (builder.produces().isEmpty()) {
            builder.produces(endpointBuilder.produces());
        }
        builder.addHeaders(endpointBuilder.headers());
        builder.addComputedHeaders(endpointBuilder.computedHeaders());

        Annotations.findFirst(REST_SERVER_STATUS, annotations)
                .ifPresent(annotation -> {
                    int code = annotation.intValue().orElse(200);
                    Optional<String> reason = annotation
                            .stringValue("reason")
                            .filter(not(String::isBlank));
                    builder.status(new HttpStatus(code, reason));
                });

        int index = 0;
        for (TypedElementInfo parameterInfo : method.parameterArguments()) {
            parameter(endpoint, method, parameterInfo, builder, index);
            index++;
        }

        endpointBuilder.addMethod(builder.build());
    }

    private void parameter(TypeInfo typeInfo,
                           TypedElementInfo methodInfo,
                           TypedElementInfo parameterInfo,
                           RestMethod.Builder method,
                           int index) {
        Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx,
                                                                                       typeInfo,
                                                                                       methodInfo,
                                                                                       parameterInfo,
                                                                                       index));
        HttpCodegenValidation.validateMethodParameterAnnotationCount(
                annotations,
                "Parameter '" + parameterInfo.elementName() + "' of declarative server method "
                        + typeInfo.typeName().fqName() + "." + methodInfo.elementName()
                        + "() must have at most one supported request parameter annotation.",
                parameterInfo.originatingElementValue());
        var parameter = RestMethodParameter.builder()
                .annotations(annotations)
                .name(parameterInfo.elementName())
                .typeName(parameterInfo.typeName())
                .index(index)
                .method(methodInfo)
                .type(typeInfo)
                .parameter(parameterInfo)
                .build();

        method.addParameter(parameter);
        if (Annotations.findFirst(HTTP_HEADER_PARAM_ANNOTATION, annotations).isPresent()) {
            method.addHeaderParameter(parameter);
        }
        if (Annotations.findFirst(HTTP_QUERY_PARAM_ANNOTATION, annotations).isPresent()) {
            method.addQueryParameter(parameter);
        }
        if (Annotations.findFirst(HTTP_PATH_PARAM_ANNOTATION, annotations).isPresent()) {
            method.addPathParameter(parameter);
        }
        if (Annotations.findFirst(HTTP_ENTITY_ANNOTATION, annotations).isPresent()) {
            method.entityParameter(parameter);
        }
    }
}
