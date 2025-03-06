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

package io.helidon.declarative.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_CONSUMES_ANNOTATION;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_METHOD_ANNOTATION;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_PATH_ANNOTATION;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_PRODUCES_ANNOTATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_QUALIFIER;
import static java.util.function.Predicate.not;

class WebServerCodegenExtension implements CodegenExtension {
    static final TypeName GENERATOR = TypeName.create(WebServerCodegenExtension.class);

    private final CodegenContext ctx;

    WebServerCodegenExtension(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypeInfo> endpoints = roundContext.annotatedTypes(HTTP_PATH_ANNOTATION);
        for (TypeInfo endpoint : endpoints) {
            process(endpoint);
        }
    }

    private void process(TypeInfo endpoint) {
        String path = endpoint.annotation(HTTP_PATH_ANNOTATION).value().orElse("/");
        String classNameBase = endpoint.typeName().classNameWithEnclosingNames().replace('.', '_');
        List<MethodDef> httpMethods = endpoint.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(it -> hasMethodAnnotation(endpoint, it))
                .map(it -> toMethodDef(endpoint, classNameBase, it))
                .toList();

        process(endpoint, classNameBase, path, httpMethods);
    }

    private MethodDef toMethodDef(TypeInfo endpoint, String endpointClassName, TypedElementInfo typedElementInfo) {
        String path = null;
        if (typedElementInfo.hasAnnotation(HTTP_PATH_ANNOTATION)) {
            path = typedElementInfo.annotation(HTTP_PATH_ANNOTATION)
                    .value()
                    .orElse("/");
        }
        String methodName = typedElementInfo.elementName();
        String className = endpointClassName + "_" + methodName + "__Service";
        String serviceMethod = "endpoint_" + methodName;
        String serviceField = serviceMethod + "_service";
        String httpMethod = httpMethodFromMethod(endpoint, typedElementInfo);
        List<ParamDef> params = typedElementInfo.parameterArguments()
                .stream()
                .map(it -> toParamDef(endpoint, it))
                .toList();

        List<String> produces = new ArrayList<>();
        List<String> consumes = new ArrayList<>();

        if (typedElementInfo.hasAnnotation(HTTP_PRODUCES_ANNOTATION)) {
            produces.addAll(typedElementInfo.annotation(HTTP_PRODUCES_ANNOTATION).stringValues().orElseGet(List::of));
        }

        if (typedElementInfo.hasAnnotation(HTTP_CONSUMES_ANNOTATION)) {
            consumes.addAll(typedElementInfo.annotation(HTTP_CONSUMES_ANNOTATION).stringValues().orElseGet(List::of));
        }

        return new MethodDef(typedElementInfo,
                             Optional.ofNullable(path),
                             className,
                             serviceField,
                             serviceMethod,
                             methodName,
                             httpMethod,
                             params,
                             produces,
                             consumes);
    }

    private ParamDef toParamDef(TypeInfo endpoint, TypedElementInfo elementInfo) {
        TypeName type = elementInfo.typeName();
        String name = elementInfo.elementName();

        List<Annotation> qualifiers = elementInfo.annotations()
                .stream()
                .filter(it -> endpoint.hasMetaAnnotation(it.typeName(),
                                                         SERVICE_ANNOTATION_QUALIFIER))
                .toList();

        return new ParamDef(type,
                            name,
                            qualifiers);
    }

    private String httpMethodFromMethod(TypeInfo endpoint, TypedElementInfo element) {
        return element.annotations()
                .stream()
                .filter(it -> endpoint.hasMetaAnnotation(it.typeName(), HTTP_METHOD_ANNOTATION))
                .findFirst()
                .flatMap(it -> endpoint.metaAnnotation(it.typeName(), HTTP_METHOD_ANNOTATION))
                .flatMap(it -> it.value())
                .map(String::toUpperCase)
                .orElseThrow(() -> new CodegenException("Could not find @HttpMethod meta annotation for method "
                                                                + element.elementName(),
                                                        element.originatingElement().orElseGet(endpoint::typeName)));
    }

    private void process(TypeInfo endpoint, String classNameBase, String path, List<MethodDef> httpMethods) {
        GenerateEndpointService.generate(ctx, endpoint, classNameBase, path, httpMethods);
    }

    private boolean hasMethodAnnotation(TypeInfo endpoint, TypedElementInfo element) {
        return element.annotations()
                .stream()
                .anyMatch(it -> endpoint.hasMetaAnnotation(it.typeName(), HTTP_METHOD_ANNOTATION));
    }
}
