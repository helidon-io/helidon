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

package io.helidon.declarative.codegen.cors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeUtils;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.common.types.TypeNames.WEIGHT;
import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG;
import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG_BUILDER_SUPPORT;
import static io.helidon.declarative.codegen.DeclarativeUtils.combinePaths;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_ALLOWED_HEADERS;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_ALLOWED_METHODS;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_ALLOWED_ORIGINS;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_ALLOW_CREDENTIALS;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_DEFAULTS;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_EXPOSE_HEADERS;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_MAX_AGE_SECONDS;
import static io.helidon.declarative.codegen.cors.CorsTypes.CORS_PATH_CONFIG;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_METHOD_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_ANNOTATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.SET_OF_STRINGS;

class CorsPathConfigGenerator {
    private static final TypeName GENERATOR = TypeName.create(CorsPathConfigGenerator.class);
    private static final TypeName CORS_CONFIG_SUPPLIER = TypeName.builder(TypeNames.SUPPLIER)
            .addTypeArgument(CORS_PATH_CONFIG)
            .build();

    private final RegistryRoundContext roundContext;

    CorsPathConfigGenerator(RegistryRoundContext roundContext) {
        this.roundContext = roundContext;
    }

    void process(TypeInfo type) {
        // this type may have an annotation on the class level, or on options method level
        // we simply generate CorsPathConfig service factory for all discovered annotations
        // annotations on method(s) always win over annotations on type, and annotations on method win over meta-annotations
        CorsConfig typeCorsConfig = corsAnnotations(type, new CorsConfig());

        // and now let's collect all annotated methods (only OPTION methods can be annotated)
        List<CorsElement> optionMethods = optionMethods(type, typeCorsConfig);

        if (typeCorsConfig.empty() && optionMethods.isEmpty()) {
            // no CORS config
            return;
        }

        String typePath = httpPath(type).orElse("/");

        // now check if we need a class level config, or if one of the option methods covers it
        boolean wantTypeConfig = true;
        for (CorsElement optionMethod : optionMethods) {
            var path = optionMethod.path();
            if (path.isEmpty()) {
                wantTypeConfig = false;
                break;
            } else {
                if (path.get().equals("/")) {
                    wantTypeConfig = false;
                    break;
                }
            }
        }

        int index = 0;

        if (wantTypeConfig) {
            generateConfig(type, typeCorsConfig, combinePaths(typePath, "*"), 70, index++);
        }
        for (CorsElement optionMethod : optionMethods) {
            if (optionMethod.path().isEmpty()) {
                generateConfig(type, optionMethod.corsConfig(), typePath, 90, index++);
            } else {
                generateConfig(type, optionMethod.corsConfig(), combinePaths(typePath, optionMethod.path().get()), 95, index++);
            }
        }
    }

    private void generateConfig(TypeInfo type, CorsConfig corsConfig, String path, int weight, int index) {
        TypeName trigger = type.typeName();
        TypeName generatedType = TypeName.builder()
                .className(toGeneratedClassName(type.typeName(), index))
                .packageName(type.typeName().packageName())
                .build();

        ClassModel.Builder classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, trigger, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, trigger, generatedType, "0", ""))
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_PER_LOOKUP))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(CORS_CONFIG_SUPPLIER)
                .addAnnotation(Annotation.builder()
                                       .typeName(WEIGHT)
                                       .putProperty("value", AnnotationProperty.create(weight))
                                       .build());

        Constructor.Builder constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        Method.Builder getMethod = Method.builder()
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(CORS_PATH_CONFIG)
                .name("get")
                .addContent("return ")
                .addContent(CORS_PATH_CONFIG)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".pathPattern(")
                .addContentLiteral(path)
                .addContentLine(")");

        if (corsConfig.defaults() == null || !corsConfig.defaults()) {
            boolean needsConfig = false;
            /*
             add all configured CORS annotations
             if an annotation contains config reference, resolve it
             */
            if (corsConfig.allowedOrigins != null) {
                needsConfig = addSet(corsConfig.allowedOrigins,
                                     "ORIGINS",
                                     "origins",
                                     "allowOrigins",
                                     classModel,
                                     constructor,
                                     getMethod);
            }
            if (corsConfig.allowedMethods != null) {

                needsConfig = addSet(corsConfig.allowedMethods,
                                     "METHODS",
                                     "methods",
                                     "allowMethods",
                                     classModel,
                                     constructor,
                                     getMethod)
                        || needsConfig;
            }
            if (corsConfig.allowedHeaders != null) {
                needsConfig = addSet(corsConfig.allowedHeaders,
                                     "HEADERS",
                                     "headers",
                                     "allowHeaders",
                                     classModel,
                                     constructor,
                                     getMethod)
                        || needsConfig;
            }
            if (corsConfig.exposedHeaders != null) {
                needsConfig = addSet(corsConfig.exposedHeaders,
                                     "EXPOSE_HEADERS",
                                     "exposeHeaders",
                                     "exposeHeaders",
                                     classModel,
                                     constructor,
                                     getMethod)
                        || needsConfig;
            }
            if (corsConfig.allowCredentials != null && corsConfig.allowCredentials) {
                getMethod.addContentLine(".allowCredentials(true)");
            }
            if (corsConfig.maxAgeSeconds != null && corsConfig.maxAgeSeconds != 3600L) {
                getMethod.addContent(".maxAge(")
                        .addContent(Duration.class)
                        .addContent(".ofSeconds(")
                        .addContent(String.valueOf(corsConfig.maxAgeSeconds()))
                        .addContentLine("L))");
            }

            if (needsConfig) {
                constructor.addParameter(CONFIG, "config");
            }
        }

        classModel.addConstructor(constructor);
        classModel.addMethod(getMethod.addContentLine(".build();")
                                     .decreaseContentPadding()
                                     .decreaseContentPadding());
        roundContext.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private boolean addSet(Set<String> theSet,
                           String constantName,
                           String fieldName,
                           String builderMethod,
                           ClassModel.Builder classModel,
                           Constructor.Builder constructor,
                           Method.Builder getMethod) {
        boolean needsConfig = false;

        for (String value : theSet) {
            if (value.contains("{") && value.contains("}")) {
                needsConfig = true;
                break;
            }
        }
        if (needsConfig) {
            // field
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(SET_OF_STRINGS)
                    .name(fieldName)
            );

            constructor.addContent("this.")
                    .addContent(fieldName)
                    .addContent(" = ")
                    .addContent(CONFIG_BUILDER_SUPPORT)
                    .addContent(".resolveSetExpressions(config, ")
                    .addContent(Set.class)
                    .addContent(".of(")
                    .addContent(theSet.stream()
                                        .map(expression -> '"' + expression + '"')
                                        .collect(Collectors.joining(", ")))
                    .addContent("));");

            getMethod.addContent(".")
                    .addContent(builderMethod)
                    .addContent("(")
                    .addContent(fieldName)
                    .addContentLine(")");
        } else {
            // constant
            classModel.addField(constant -> constant
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .name(constantName)
                    .type(SET_OF_STRINGS)
                    .addContent(Set.class)
                    .addContent(".of(")
                    .update(it -> it.addContent(theSet.stream()
                                                        .map(value -> '"' + value + '"')
                                                        .collect(Collectors.joining(", "))))
                    .addContent(")")
            );
            getMethod.addContent(".")
                    .addContent(builderMethod)
                    .addContent("(")
                    .addContent(constantName)
                    .addContentLine(")");
        }

        return needsConfig;
    }

    private String toGeneratedClassName(TypeName typeName, int index) {
        String name = typeName.classNameWithEnclosingNames().replace('.', '_');
        name += "__CorsConfig";
        if (index == 0) {
            return name;
        }
        return name + index;
    }

    private List<CorsElement> optionMethods(TypeInfo type, CorsConfig typeCorsConfig) {
        List<CorsElement> optionMethods = new ArrayList<>();
        for (TypedElementInfo element : type.elementInfo()) {
            CorsConfig elementCorsConfig = corsAnnotations(element, new CorsConfig());
            if (elementCorsConfig.empty()) {
                continue;
            }
            if (element.kind() != ElementKind.METHOD) {
                throw new CodegenException("CORS annotations are only allowed on HTTP OPTIONS methods, annotated element"
                                                   + "is " + element.kind(),
                                           element.originatingElementValue());
            }
            if (element.accessModifier() == AccessModifier.PRIVATE) {
                throw new CodegenException("CORS annotations are only allowed on non-private methods",
                                           element.originatingElementValue());
            }
            var httpMethod = DeclarativeUtils.findMetaAnnotated(element.annotations(), HTTP_METHOD_ANNOTATION);
            if (httpMethod.isEmpty()) {
                throw new CodegenException("CORS annotations are only allowed on HTTP OPTIONS methods, annotated method"
                                                   + " does not have an HTTP Method annotation",
                                           element.originatingElementValue());
            }
            // the value is required
            String method = httpMethod.get().value().orElseThrow();
            if (method.equals("OPTIONS")) {
                optionMethods.add(new CorsElement(typeCorsConfig.combine(elementCorsConfig), element, httpPath(element)));
            } else {
                throw new CodegenException("CORS annotations are only allowed on HTTP OPTIONS methods, annotated method"
                                                   + " is " + method,
                                           element.originatingElementValue());
            }
        }

        return optionMethods;
    }

    private Optional<String> httpPath(Annotated element) {
        return element.findAnnotation(HTTP_PATH_ANNOTATION)
                .flatMap(Annotation::stringValue);
    }

    private CorsConfig corsAnnotations(Annotated element,
                                       CorsConfig existing) {
        // direct annotations
        if (element.hasAnnotation(CORS_DEFAULTS)) {
            if (existing.empty()) {
                return new CorsConfig(true);
            }
        }

        /*
        First go through all meta-annotations, as annotations directly on this element always "win"
         */
        Set<TypeName> visited = new HashSet<>();
        CorsConfig inProgress = existing;
        for (Annotation annotation : element.allAnnotations()) {
            inProgress = fromMetaAnnotations(annotation, inProgress, visited);
        }

        return fromDirectAnnotations(element, existing);

    }

    private CorsConfig fromMetaAnnotations(Annotation annotation, CorsConfig original, Set<TypeName> visited) {
        if (!visited.add(annotation.typeName())) {
            return original;
        }

        CorsConfig inProgress = original;
        for (Annotation metaAnnotation : annotation.metaAnnotations()) {
            inProgress = fromMetaAnnotations(metaAnnotation, inProgress, visited);
        }
        return inProgress;
    }

    private CorsConfig fromDirectAnnotations(Annotated element, CorsConfig existing) {
        Set<String> allowedOrigins = null;
        Set<String> allowedMethods = null;
        Set<String> allowedHeaders = null;
        Set<String> exposedHeaders = null;
        Boolean allowCredentials = null;
        Long maxAge = null;

        if (element.hasAnnotation(CORS_ALLOWED_ORIGINS)) {
            allowedOrigins = Set.copyOf(element.annotation(CORS_ALLOWED_ORIGINS).stringValues().orElseGet(List::of));
        }

        if (element.hasAnnotation(CORS_ALLOWED_METHODS)) {
            allowedMethods = Set.copyOf(element.annotation(CORS_ALLOWED_METHODS).stringValues().orElseGet(List::of));
        }

        if (element.hasAnnotation(CORS_ALLOWED_HEADERS)) {
            allowedHeaders = Set.copyOf(element.annotation(CORS_ALLOWED_HEADERS).stringValues().orElseGet(List::of));
        }

        if (element.hasAnnotation(CORS_EXPOSE_HEADERS)) {
            exposedHeaders = Set.copyOf(element.annotation(CORS_EXPOSE_HEADERS).stringValues().orElseGet(List::of));
        }

        if (element.hasAnnotation(CORS_ALLOW_CREDENTIALS)) {
            allowCredentials = element.annotation(CORS_ALLOW_CREDENTIALS)
                    .booleanValue()
                    .orElse(true);
        }

        if (element.hasAnnotation(CORS_MAX_AGE_SECONDS)) {
            maxAge = element.annotation(CORS_MAX_AGE_SECONDS)
                    .longValue()
                    .orElse(3600L);
        }

        return existing.combine(allowedOrigins, allowedMethods, allowedHeaders, exposedHeaders, allowCredentials, maxAge);
    }

    private record CorsElement(CorsConfig corsConfig, TypedElementInfo element, Optional<String> path) {

    }

    private record CorsConfig(Boolean defaults,
                              Set<String> allowedOrigins,
                              Set<String> allowedMethods,
                              Set<String> allowedHeaders,
                              Set<String> exposedHeaders,
                              Boolean allowCredentials,
                              Long maxAgeSeconds) {
        CorsConfig() {
            this(null, null, null, null, null, null, null);
        }

        CorsConfig(boolean defaults) {
            this(defaults, null, null, null, null, null, null);
        }

        CorsConfig combine(CorsConfig other) {
            return combine(other.allowedOrigins,
                           other.allowedMethods,
                           other.allowedHeaders,
                           other.exposedHeaders,
                           other.allowCredentials,
                           other.maxAgeSeconds);
        }

        CorsConfig combine(Set<String> allowedOrigins,
                           Set<String> allowedMethods,
                           Set<String> allowedHeaders,
                           Set<String> exposedHeaders,
                           Boolean allowCredentials,
                           Long maxAgeSeconds) {
            if (allowedOrigins == null
                    && allowedMethods == null
                    && allowedHeaders == null
                    && exposedHeaders == null
                    && allowCredentials == null
                    && maxAgeSeconds == null
                    && defaults != null) {
                // no updates
                return this;
            }
            // something has changed, defaults no longer apply
            return new CorsConfig(
                    null,
                    allowedOrigins == null ? allowedOrigins() : allowedOrigins,
                    allowedMethods == null ? allowedMethods() : allowedMethods,
                    allowedHeaders == null ? allowedHeaders() : allowedHeaders,
                    exposedHeaders == null ? exposedHeaders() : exposedHeaders,
                    allowCredentials == null ? allowCredentials() : allowCredentials,
                    maxAgeSeconds == null ? maxAgeSeconds() : maxAgeSeconds
            );
        }

        boolean empty() {
            return defaults == null
                    && allowedOrigins == null
                    && allowedMethods == null
                    && allowedHeaders == null
                    && exposedHeaders == null
                    && allowCredentials == null
                    && maxAgeSeconds == null;
        }
    }
}
