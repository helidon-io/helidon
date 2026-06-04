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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.model.http.RestMethodParameter;

import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_OBJECT;
import static java.util.function.Predicate.not;

final class OpenApiFormRequestBodyCodegen {
    static final String FORM_MEDIA_TYPE = "application/x-www-form-urlencoded";

    private static final TypeName VOID = TypeName.create(Void.class);

    private final OpenApiAnnotationValidator validator;
    private final OpenApiSourceExpressions expressions;
    private final OpenApiSchemaCodegen schemas;
    private final Function<List<Annotation>, String> examplesExpression;
    private final Predicate<RestMethodParameter> requiredPredicate;
    private final Function<RestMethodParameter, String> formParameterName;

    OpenApiFormRequestBodyCodegen(OpenApiAnnotationValidator validator,
                                  OpenApiSourceExpressions expressions,
                                  OpenApiSchemaCodegen schemas,
                                  Function<List<Annotation>, String> examplesExpression,
                                  Predicate<RestMethodParameter> requiredPredicate,
                                  Function<RestMethodParameter, String> formParameterName) {
        this.validator = validator;
        this.expressions = expressions;
        this.schemas = schemas;
        this.examplesExpression = examplesExpression;
        this.requiredPredicate = requiredPredicate;
        this.formParameterName = formParameterName;
    }

    void addRequestBody(Method.Builder method,
                        String restMethodDescription,
                        Annotation requestBody,
                        List<String> consumes,
                        List<RestMethodParameter> formParameters,
                        Map<TypeName, String> componentNames) {
        List<Annotation> contentAnnotations = requestBody == null
                ? List.of()
                : requestBody.annotationValues("content").orElseGet(List::of);
        validateFormParameters(restMethodDescription, formParameters);
        validateConsumes(restMethodDescription, consumes);
        validator.validateContentMediaTypes("@OpenApi.RequestBody on " + restMethodDescription,
                                            contentAnnotations,
                                            List.of(FORM_MEDIA_TYPE));
        validateFormContentMediaTypes(restMethodDescription, contentAnnotations);
        method.addContentLine(".requestBody(requestBody -> requestBody")
                .increaseContentPadding()
                .increaseContentPadding();
        if (requestBody != null) {
            requestBody.stringValue()
                    .filter(not(String::isBlank))
                    .ifPresent(description -> method.addContent(".description(")
                            .addContent(expressions.stringExpression(description))
                            .addContentLine(")"));
        }
        Optional<Boolean> requiredOverride = requestBody == null
                ? Optional.empty()
                : required(requestBody);
        boolean required = requiredOverride.orElse(formParameters.stream().anyMatch(requiredPredicate));
        if (required || requiredOverride.isPresent()) {
            method.addContent(".required(")
                    .addContent(Boolean.toString(required))
                    .addContentLine(")");
        }
        if (contentAnnotations.isEmpty()) {
            addFormContent(method, formParameters, componentNames, List.of());
        } else {
            for (Annotation content : contentAnnotations) {
                addFormContent(method,
                               formParameters,
                               componentNames,
                               content.annotationValues("examples").orElseGet(List::of));
            }
        }
        method.addContentLine(")")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void validateFormParameters(String restMethodDescription, List<RestMethodParameter> formParameters) {
        Set<String> names = new HashSet<>();
        for (RestMethodParameter formParameter : formParameters) {
            String name = formParameterName.apply(formParameter);
            if (!names.add(name)) {
                throw new CodegenException("Generated OpenAPI form request body on " + restMethodDescription
                                                   + " cannot define form field " + name + " more than once");
            }
        }
    }

    private void validateConsumes(String restMethodDescription, List<String> consumes) {
        if (consumes.isEmpty() || consumes.equals(List.of(FORM_MEDIA_TYPE))) {
            return;
        }
        throw new CodegenException("Generated OpenAPI form request body on " + restMethodDescription
                                           + " requires @Http.Consumes(\"" + FORM_MEDIA_TYPE + "\")"
                                           + " when consumes is explicitly declared");
    }

    private void addFormContent(Method.Builder method,
                                List<RestMethodParameter> formParameters,
                                Map<TypeName, String> componentNames,
                                List<Annotation> examples) {
        method.addContent(".content(")
                .addContent(expressions.validatedStringExpression(FORM_MEDIA_TYPE))
                .addContent(", ")
                .addContent(formMediaTypeConsumer(formParameters, componentNames, examples))
                .addContentLine(")");
    }

    private void validateFormContentMediaTypes(String restMethodDescription, List<Annotation> contentAnnotations) {
        for (Annotation content : contentAnnotations) {
            for (String mediaType : validator.contentMediaTypes(content, List.of(FORM_MEDIA_TYPE))) {
                if (!FORM_MEDIA_TYPE.equals(mediaType)) {
                    throw new CodegenException("@OpenApi.RequestBody on " + restMethodDescription
                                                       + " for @Http.FormParam parameters must use "
                                                       + FORM_MEDIA_TYPE + " content");
                }
            }
            if (content.typeValue("schema").filter(Predicate.not(VOID::equals)).isPresent()
                    || content.typeValue("itemSchema").filter(Predicate.not(VOID::equals)).isPresent()) {
                throw new CodegenException("@OpenApi.RequestBody on " + restMethodDescription
                                                   + " for @Http.FormParam parameters cannot override the inferred"
                                                   + " form schema");
            }
        }
    }

    private String formMediaTypeConsumer(List<RestMethodParameter> formParameters,
                                         Map<TypeName, String> componentNames,
                                         List<Annotation> examples) {
        return "content -> content.schema(" + formSchemaExpression(formParameters, componentNames) + ")"
                + examplesExpression.apply(examples);
    }

    private String formSchemaExpression(List<RestMethodParameter> formParameters, Map<TypeName, String> componentNames) {
        StringBuilder result = new StringBuilder(JSON_OBJECT.fqName())
                .append(".builder()")
                .append(".set(\"type\", \"object\")")
                .append(".set(\"properties\", properties -> properties");
        for (RestMethodParameter formParameter : formParameters) {
            TypeName schemaType = schemas.schemaType(formParameter.typeName());
            result.append(".set(")
                    .append(expressions.validatedStringExpression(formParameterName.apply(formParameter)))
                    .append(", ")
                    .append(schemas.schemaExpression(schemaType, componentNames))
                    .append(")");
        }
        result.append(")");
        List<String> requiredNames = formParameters.stream()
                .filter(requiredPredicate)
                .map(formParameterName)
                .toList();
        if (!requiredNames.isEmpty()) {
            result.append(".setStrings(\"required\", java.util.List.of(");
            for (int i = 0; i < requiredNames.size(); i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(expressions.stringLiteral(requiredNames.get(i)));
            }
            result.append("))");
        }
        return result.append(".build()").toString();
    }

    private Optional<Boolean> required(Annotation annotation) {
        return annotation.stringValue("required")
                .map(this::enumName)
                .flatMap(value -> switch (value) {
                case "TRUE" -> Optional.of(true);
                case "FALSE" -> Optional.of(false);
                case "UNSPECIFIED" -> Optional.empty();
                default -> throw new CodegenException("@OpenApi.Required has unsupported value: " + value);
                });
    }

    private String enumName(String value) {
        int dot = value.lastIndexOf('.');
        return dot == -1 ? value : value.substring(dot + 1);
    }
}
