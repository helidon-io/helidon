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

package io.helidon.declarative.codegen.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.model.http.ComputedHeader;
import io.helidon.declarative.codegen.model.http.HeaderValue;
import io.helidon.declarative.codegen.model.http.HttpAnnotated;
import io.helidon.declarative.codegen.model.http.HttpMethod;
import io.helidon.declarative.codegen.model.http.RestEndpoint;
import io.helidon.declarative.codegen.model.http.RestMethod;
import io.helidon.service.codegen.FieldHandler;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_CONSUMES_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PRODUCES_ANNOTATION;

/**
 * A base class for HTTP Rest extensions (server and client).
 */
public abstract class RestExtensionBase {
    /**
     * Constructor with no side effects.
     */
    protected RestExtensionBase() {
    }

    /**
     * Extract HTTP Method from its annotation.
     *
     * @param element              annotated element
     * @param httpMethodAnnotation Http method annotation
     * @return http method abstraction for code generation
     */
    protected HttpMethod httpMethodFromAnnotation(TypedElementInfo element, Annotation httpMethodAnnotation) {
        String method = httpMethodAnnotation.stringValue()
                .map(String::toUpperCase)
                .orElseThrow(() -> new CodegenException("Could not find @HttpMethod meta annotation for method "
                                                                + element.elementName(),
                                                        element.originatingElementValue()));
        return switch (method) {
            case "GET", "PUT", "POST", "DELETE", "HEAD", "OPTIONS", "PATCH", "TRACE" -> new HttpMethod(method, true);
            default -> new HttpMethod(method, false);
        };
    }

    /**
     * Find path in the annotations and set it on the builder.
     *
     * @param annotations all element annotations
     * @param builder     element builder
     */
    protected void path(Set<Annotation> annotations, HttpAnnotated.BuilderBase<?, ?> builder) {
        Annotations.findFirst(HTTP_PATH_ANNOTATION, annotations)
                .flatMap(Annotation::stringValue)
                .ifPresent(builder::path);
    }

    /**
     * Find consumes in the annotations and set it on the builder.
     *
     * @param annotations all element annotations
     * @param builder     element builder
     */
    protected void consumes(Set<Annotation> annotations, HttpAnnotated.BuilderBase<?, ?> builder) {
        Annotations.findFirst(HTTP_CONSUMES_ANNOTATION, annotations)
                .flatMap(Annotation::stringValues)
                .ifPresent(builder::consumes);
    }

    /**
     * Find produces in the annotations and set it on the builder.
     *
     * @param annotations all element annotations
     * @param builder     element builder
     */
    protected void produces(Set<Annotation> annotations, HttpAnnotated.BuilderBase<?, ?> builder) {
        Annotations.findFirst(HTTP_PRODUCES_ANNOTATION, annotations)
                .flatMap(Annotation::stringValues)
                .ifPresent(builder::produces);
    }

    /**
     * Find headers in the annotations and set it on the builder.
     *
     * @param annotations            all element annotations
     * @param builder                element builder
     * @param repeatedAnnotationType type of the annotation to be found (repeated)
     * @param singleAnnotationType   type of the annotation to be found (single)
     */
    protected void headers(Set<Annotation> annotations,
                           HttpAnnotated.BuilderBase<?, ?> builder,
                           TypeName repeatedAnnotationType,
                           TypeName singleAnnotationType) {
        AtomicBoolean found = new AtomicBoolean(false);
        Annotations.findFirst(repeatedAnnotationType, annotations)
                .flatMap(Annotation::annotationValues)
                .stream()
                .flatMap(List::stream)
                .forEach(headerAnnotation -> {
                    found.set(true);
                    String name = headerAnnotation.stringValue("name").orElseThrow();
                    String value = headerAnnotation.stringValue("value").orElseThrow();
                    builder.addHeader(new HeaderValue(name, value));
                });

        if (!found.get()) {
            Annotations.findFirst(singleAnnotationType, annotations)
                    .ifPresent(headerAnnotation -> {
                        String name = headerAnnotation.stringValue("name").orElseThrow();
                        String value = headerAnnotation.stringValue("value").orElseThrow();
                        builder.addHeader(new HeaderValue(name, value));
                    });
        }
    }

    /**
     * Find computed headers in the annotations and set it on the builder.
     *
     * @param annotations            all element annotations
     * @param builder                element builder
     * @param repeatedAnnotationType type of the annotation to be found (repeated)
     * @param singleAnnotationType   type of the annotation to be found (single)
     */
    protected void computedHeaders(Set<Annotation> annotations,
                                   HttpAnnotated.BuilderBase<?, ?> builder,
                                   TypeName repeatedAnnotationType,
                                   TypeName singleAnnotationType) {
        AtomicBoolean found = new AtomicBoolean(false);
        Annotations.findFirst(repeatedAnnotationType, annotations)
                .flatMap(Annotation::annotationValues)
                .stream()
                .flatMap(List::stream)
                .forEach(headerAnnotation -> {
                    found.set(true);
                    String name = headerAnnotation.stringValue("name").orElseThrow();
                    TypeName producer = headerAnnotation.typeValue("producerClass").orElseThrow();
                    builder.addComputedHeader(new ComputedHeader(name, producer));
                });
        if (!found.get()) {
            Annotations.findFirst(singleAnnotationType, annotations)
                    .ifPresent(headerAnnotation -> {
                        String name = headerAnnotation.stringValue("name").orElseThrow();
                        TypeName producer = headerAnnotation.typeValue("producerClass").orElseThrow();
                        builder.addComputedHeader(new ComputedHeader(name, producer));
                    });
        }
    }

    /**
     * Create a type name that is a supplier of the provided type.
     *
     * @param type type to supply
     * @return supplier type
     */
    protected TypeName supplierOf(TypeName type) {
        return TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(type)
                .build();
    }

    protected Map<TypeName, String> headerProducers(FieldHandler fieldHandler, RestEndpoint endpoint) {
        Map<TypeName, String> result = new HashMap<>();

        // now for each method
        for (RestMethod method : endpoint.methods()) {
            method.computedHeaders()
                    .stream()
                    .map(ComputedHeader::producer)
                    .forEach(it -> {
                        String fieldName = ensureHeaderProducerField(fieldHandler, it);
                        result.put(it, fieldName);
                    });
        }
        return result;
    }

    protected Optional<Annotation> findMetaAnnotated(TypedElementInfo method,
                                                     TypeName metaAnnotation,
                                                     Set<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.typeName().equals(metaAnnotation)) {
                return Optional.of(annotation);
            }
            if (annotation.hasMetaAnnotation(metaAnnotation)) {
                return Annotations.findFirst(metaAnnotation, annotation.metaAnnotations());
            }
        }
        return Optional.empty();
    }

    private String ensureHeaderProducerField(FieldHandler fieldHandler, TypeName producer) {
        return fieldHandler.field(producer,
                                  "headerProducer_",
                                  AccessModifier.PRIVATE,
                                  producer,
                                  field -> {
                                  },
                                  (constructor, fieldName) -> constructor
                                          .addParameter(param -> param
                                                  .type(producer)
                                                  .name(fieldName))
                                          .addContent("this.")
                                          .addContent(fieldName)
                                          .addContent(" = ")
                                          .addContent(fieldName)
                                          .addContentLine(";"));
    }
}
