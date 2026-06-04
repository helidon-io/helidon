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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.JSON_OBJECT;

final class OpenApiSchemaCodegen {
    private static final TypeName VOID = TypeName.create(Void.class);

    private final OpenApiSourceExpressions expressions;
    private final Function<List<Annotation>, String> examplesExpression;

    OpenApiSchemaCodegen(OpenApiSourceExpressions expressions, Function<List<Annotation>, String> examplesExpression) {
        this.expressions = expressions;
        this.examplesExpression = examplesExpression;
    }

    void addSchemaComponent(Method.Builder method, OpenApiSchemaBinding schemaBinding) {
        method.addContent("componentSchema(document, ")
                .addContent(schemaBinding.fieldName())
                .addContent(", ")
                .addContent(expressions.stringLiteral(schemaBinding.name()))
                .addContentLine(");");
    }

    Map<TypeName, String> componentNames(List<OpenApiSchemaBinding> schemaBindings) {
        Map<TypeName, String> result = new LinkedHashMap<>();
        for (OpenApiSchemaBinding schemaBinding : schemaBindings) {
            result.put(schemaBinding.type(), schemaBinding.name());
        }
        return result;
    }

    String mediaTypeConsumer(String schemaExpression) {
        return "content -> content.schema(" + schemaExpression + ")";
    }

    String mediaTypeConsumer(Annotation content,
                             TypeName inferredSchemaType,
                             boolean hasInferredSchema,
                             Map<TypeName, String> componentNames) {
        Optional<TypeName> explicitSchema = content.typeValue("schema")
                .filter(Predicate.not(VOID::equals));
        TypeName schemaType = explicitSchema.orElse(inferredSchemaType);
        boolean hasSchema = explicitSchema.isPresent() || hasInferredSchema;
        StringBuilder result = new StringBuilder("content -> content.schema(")
                .append(hasSchema ? schemaExpression(schemaType, componentNames) : JSON_OBJECT.fqName() + ".builder().build()")
                .append(")");
        content.typeValue("itemSchema")
                .filter(Predicate.not(VOID::equals))
                .ifPresent(itemSchema -> result.append(".itemSchema(")
                        .append(schemaExpression(itemSchema, componentNames))
                        .append(")"));
        result.append(examplesExpression.apply(content.annotationValues("examples").orElseGet(List::of)));
        return result.toString();
    }

    String schemaExpression(TypeName type) {
        return schemaExpression(type, Map.of());
    }

    String schemaExpression(TypeName type, Map<TypeName, String> componentNames) {
        TypeName schemaType = schemaType(type);
        if (schemaType.isList()) {
            TypeName itemType = schemaType.typeArguments().isEmpty()
                    ? TypeNames.STRING
                    : schemaType.typeArguments().getFirst();
            return "arraySchema(" + schemaExpression(itemType, componentNames) + ")";
        }
        return jsonType(schemaType)
                .map(it -> "schema(" + expressions.stringLiteral(it) + ")")
                .orElseGet(() -> schemaRefExpression(schemaType, componentNames));
    }

    String stringSchemaWithDefaultExpression(String value) {
        return JSON_OBJECT.fqName() + ".builder()"
                + ".set(\"type\", \"string\")"
                + ".set(\"default\", " + expressions.stringLiteral(value) + ")"
                + ".build()";
    }

    TypeName schemaType(TypeName type) {
        TypeName unwrapped = type.isOptional() && !type.typeArguments().isEmpty()
                ? type.typeArguments().getFirst()
                : type;
        return unwrapped.boxed();
    }

    TypeName responseType(TypeName type) {
        return schemaType(type);
    }

    boolean hasResponseEntity(TypeName type) {
        TypeName boxed = schemaType(type);
        return !boxed.equals(TypeNames.BOXED_VOID);
    }

    void collectSchemaComponent(Set<TypeName> schemaTypes, TypeName type) {
        TypeName schemaType = schemaType(type);
        if (schemaType.isList()) {
            if (!schemaType.typeArguments().isEmpty()) {
                collectSchemaComponent(schemaTypes, schemaType.typeArguments().getFirst());
            }
            return;
        }
        if (jsonType(schemaType).isPresent()) {
            return;
        }
        schemaTypes.add(schemaType);
    }

    String schemaName(TypeName typeName) {
        return typeName.classNameWithEnclosingNames().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    String schemaFieldName(String schemaName) {
        StringBuilder result = new StringBuilder(schemaName.length() + "Schema".length());
        for (int i = 0; i < schemaName.length(); i++) {
            char ch = schemaName.charAt(i);
            if (result.isEmpty()) {
                if (Character.isJavaIdentifierStart(ch)) {
                    result.append(Character.toLowerCase(ch));
                } else if (Character.isJavaIdentifierPart(ch)) {
                    result.append('_').append(ch);
                } else {
                    result.append('_');
                }
                continue;
            }
            result.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
        }
        return result.append("Schema").toString();
    }

    String uniqueSchemaName(String schemaName, Set<String> usedSchemaNames) {
        if (usedSchemaNames.add(schemaName)) {
            return schemaName;
        }

        int index = 2;
        String candidate = schemaName + index;
        while (!usedSchemaNames.add(candidate)) {
            index++;
            candidate = schemaName + index;
        }
        return candidate;
    }

    String uniqueFieldName(String fieldName, Set<String> usedFieldNames) {
        if (usedFieldNames.add(fieldName)) {
            return fieldName;
        }

        int index = 2;
        String candidate = fieldName + index;
        while (!usedFieldNames.add(candidate)) {
            index++;
            candidate = fieldName + index;
        }
        return candidate;
    }

    private String schemaRefExpression(TypeName type, Map<TypeName, String> componentNames) {
        TypeName schemaType = schemaType(type);
        String schemaName = componentNames.getOrDefault(schemaType, schemaName(schemaType));
        return "schemaRef(" + expressions.stringLiteral(schemaName) + ")";
    }

    private Optional<String> jsonType(TypeName type) {
        TypeName boxed = type.boxed().genericTypeName();
        if (boxed.equals(TypeNames.STRING)) {
            return Optional.of("string");
        }
        if (boxed.equals(TypeNames.BOXED_BOOLEAN)) {
            return Optional.of("boolean");
        }
        if (boxed.equals(TypeNames.BOXED_BYTE)
                || boxed.equals(TypeNames.BOXED_SHORT)
                || boxed.equals(TypeNames.BOXED_INT)
                || boxed.equals(TypeNames.BOXED_LONG)) {
            return Optional.of("integer");
        }
        if (boxed.equals(TypeNames.BOXED_FLOAT)
                || boxed.equals(TypeNames.BOXED_DOUBLE)
                || boxed.equals(TypeName.create("java.math.BigDecimal"))) {
            return Optional.of("number");
        }
        return Optional.empty();
    }
}
