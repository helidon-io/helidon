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

package io.helidon.declarative.codegen.websocket.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.http.HttpTypes;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.websocket.client.WebSocketClientTypes.ANNOTATION_ENDPOINT;

class WebSocketClientExtension implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(WebSocketClientExtension.class);
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{(.*?)}");
    private final RegistryCodegenContext ctx;

    WebSocketClientExtension(RegistryCodegenContext ctx) {
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

    private void process(RegistryRoundContext roundContext, TypeInfo clientEndpoint) {
        if (clientEndpoint.kind() == ElementKind.INTERFACE) {
            throw new CodegenException("Interfaces should not be annotated with " + ANNOTATION_ENDPOINT.fqName(),
                                       clientEndpoint.originatingElementValue());
        }

        TypeName endpointType = clientEndpoint.typeName();
        // we need to generate two types - WsClientEndpointFactory and WsListener
        String classNameBase = endpointType.classNameWithEnclosingNames().replace('.', '_');

        String listenerClassName = classNameBase + "__WsListener";

        Annotation annotation = clientEndpoint.annotation(ANNOTATION_ENDPOINT);
        String factoryClassName = annotation.stringValue("factoryClassName").orElse("");
        factoryClassName = factoryClassName.isBlank() ? classNameBase + "Factory" : factoryClassName;

        TypeName generatedListener = TypeName.builder()
                .packageName(endpointType.packageName())
                .className(listenerClassName)
                .build();
        TypeName generatedFactory = TypeName.builder()
                .packageName(endpointType.packageName())
                .className(factoryClassName)
                .build();

        var pathParams = pathParameters(clientEndpoint, annotation);

        WebSocketClientFactoryGenerator.generate(roundContext,
                                                 clientEndpoint,
                                                 endpointType,
                                                 generatedFactory,
                                                 generatedListener,
                                                 pathParams);
        WebSocketClientListenerGenerator.generate(roundContext,
                                                  clientEndpoint,
                                                  endpointType,
                                                  generatedListener,
                                                  pathParams);
    }

    // map of path parameter name to path parameter type
    private Map<String, TypeName> pathParameters(TypeInfo clientEndpoint, Annotation endpointAnnotation) {
        // validate that endpoint does not contain path parameters (only allowed in Http.Path)
        String endpoint = endpointAnnotation.stringValue().orElse("");
        // if it contains { not prefixed by $, we are in trouble
        validateEndpointDoesNotHavePathParameters(clientEndpoint, endpoint);

        Set<String> pathParamNamesInPath = new LinkedHashSet<>();
        Optional<Annotation> annotation = clientEndpoint.findAnnotation(HttpTypes.HTTP_PATH_ANNOTATION);
        annotation.ifPresent(value -> getPathParamsFromPath(pathParamNamesInPath, value.value().orElse("")));

        // and if any method contains @Http.PathParam annotation, make sure it is a valid path param,
        // and that we only define it as a single type
        Map<String, TypeName> pathParams = new HashMap<>();

        findPathParamsInMethods(clientEndpoint, pathParamNamesInPath, pathParams);

        for (String name : pathParamNamesInPath) {
            pathParams.putIfAbsent(name, TypeNames.STRING);
        }

        Map<String, TypeName> orderedPathParams = new LinkedHashMap<>();
        for (String name : pathParamNamesInPath) {
            orderedPathParams.put(name, pathParams.get(name));
        }

        return orderedPathParams;
    }

    private void validateEndpointDoesNotHavePathParameters(TypeInfo clientEndpoint, String endpoint) {
        int i = 0;
        while (i < endpoint.length()) {
            i = endpoint.indexOf('{', i);
            if (i == -1) {
                break;
            }
            if (i == 0) {
                throw new CodegenException("Invalid path parameters in endpoint. Endpoint can only define "
                                                   + "config references using ${}, but never path parameters using {}"
                                                   + endpoint,
                                           clientEndpoint.originatingElementValue());
            }
            if (endpoint.charAt(i - 1) == '$') {
                i++;
                continue;
            }
            throw new CodegenException("Invalid path parameters in endpoint. Endpoint can only define "
                                               + "config references using ${}, but never path parameters using {}"
                                               + endpoint,
                                       clientEndpoint.originatingElementValue());
        }
    }

    private void findPathParamsInMethods(TypeInfo clientEndpoint,
                                         Set<String> pathParamNamesInPath,
                                         Map<String, TypeName> pathParams) {
        List<TypedElementInfo> annotatedParams = clientEndpoint.elementInfo()
                .stream()
                .flatMap(it -> it.parameterArguments().stream())
                .filter(ElementInfoPredicates.hasAnnotation(HttpTypes.HTTP_PATH_PARAM_ANNOTATION))
                .toList();

        for (TypedElementInfo annotatedParam : annotatedParams) {
            TypeName paramType = annotatedParam.typeName();
            var pathParamName = annotatedParam.annotation(HttpTypes.HTTP_PATH_PARAM_ANNOTATION)
                    .stringValue()
                    .orElse("");
            if (!pathParamNamesInPath.contains(pathParamName)) {
                throw new CodegenException("@Http.PathParam annotation for a parameter name that is not present"
                                                   + " in the @Http.Path of the endpoint: " + pathParamName,
                                           annotatedParam.originatingElementValue());
            }
            TypeName typeName = pathParams.get(pathParamName);
            if (typeName != null && !typeName.equals(paramType)) {
                throw new CodegenException("@Http.PathParam annotation for a parameter uses two distinct types in the"
                                                   + "same endpoint for param name: " + pathParamName,
                                           annotatedParam.originatingElementValue());
            }
            pathParams.put(pathParamName, paramType);
        }
    }

    private void getPathParamsFromPath(Set<String> pathParamNamesInPath, String path) {
        // /websocket/echo/{user}/{shard}
        var matcher = PATH_PARAM_PATTERN.matcher(path);
        while (matcher.find()) {
            pathParamNamesInPath.add(matcher.group(1));
        }
    }
}
