/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.json.schema.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.json.JsonException;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;

import static java.util.function.Predicate.not;

record SchemaInfo(TypeName generatedSchema, JsonObject schema) {

    private static final Map<TypeName, TypeName> BOXED_TO_PRIMITIVE = Map.of(
            TypeNames.BOXED_BOOLEAN, TypeNames.PRIMITIVE_BOOLEAN,
            TypeNames.BOXED_BYTE, TypeNames.PRIMITIVE_BYTE,
            TypeNames.BOXED_SHORT, TypeNames.PRIMITIVE_SHORT,
            TypeNames.BOXED_INT, TypeNames.PRIMITIVE_INT,
            TypeNames.BOXED_LONG, TypeNames.PRIMITIVE_LONG,
            TypeNames.BOXED_CHAR, TypeNames.PRIMITIVE_CHAR,
            TypeNames.BOXED_FLOAT, TypeNames.PRIMITIVE_FLOAT,
            TypeNames.BOXED_DOUBLE, TypeNames.PRIMITIVE_DOUBLE,
            TypeNames.BOXED_VOID, TypeNames.PRIMITIVE_VOID
    );

    private static final Set<TypeName> INTEGERS = Set.of(TypeNames.PRIMITIVE_BYTE,
                                                         TypeNames.PRIMITIVE_SHORT,
                                                         TypeNames.PRIMITIVE_INT,
                                                         TypeNames.PRIMITIVE_LONG,
                                                         SchemaTypes.BIG_INTEGER);

    private static final Set<TypeName> NUMBERS = Set.of(TypeNames.PRIMITIVE_FLOAT,
                                                        TypeNames.PRIMITIVE_DOUBLE,
                                                        SchemaTypes.BIG_DECIMAL,
                                                        SchemaTypes.NUMBER);

    private static final Set<TypeName> DEFAULT_ANNOTATIONS = Set.of(SchemaTypes.JSON_SCHEMA_DEFAULT,
                                                                   SchemaTypes.JSON_SCHEMA_DEFAULT_INT,
                                                                   SchemaTypes.JSON_SCHEMA_DEFAULT_LONG,
                                                                   SchemaTypes.JSON_SCHEMA_DEFAULT_DOUBLE,
                                                                   SchemaTypes.JSON_SCHEMA_DEFAULT_BOOLEAN,
                                                                   SchemaTypes.JSON_SCHEMA_DEFAULT_JSON);

    private static final Pattern JSON_NUMBER_PATTERN =
            Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    static SchemaInfo create(TypeInfo annotatedType, CodegenContext ctx) {
        TypeName annotatedTypeName = annotatedType.typeName();
        TypeName generatedTypeName = TypeName.builder()
                .from(annotatedTypeName)
                .className(annotatedTypeName.className() + "__JsonSchema")
                .build();

        JsonObject.Builder schemaBuilder = JsonObject.builder()
                .set("$schema", "https://json-schema.org/draft/2020-12/schema");
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_ID)
                .flatMap(it -> it.stringValue())
                .ifPresent(it -> schemaBuilder.set("$id", it));
        processObject(schemaBuilder, annotatedType, ctx, new AtomicBoolean());
        return new SchemaInfo(generatedTypeName, schemaBuilder.build());
    }

    private static void processCommonAnnotations(JsonObject.Builder builder, Annotated annotatedType, AtomicBoolean required) {
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_TITLE)
                .flatMap(it -> it.stringValue())
                .ifPresent(it -> builder.set("title", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_DESCRIPTION)
                .flatMap(it -> it.stringValue())
                .ifPresent(it -> builder.set("description", it));
        processDefaultAnnotation(builder, annotatedType);
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_REQUIRED).ifPresent(it -> required.set(true));
    }

    private static void processDefaultAnnotation(JsonObject.Builder builder, Annotated annotated) {
        List<Annotation> defaultAnnotations = annotated.annotations()
                .stream()
                .filter(annotation -> DEFAULT_ANNOTATIONS.contains(annotation.typeName()))
                .toList();
        if (defaultAnnotations.size() > 1) {
            throw new CodegenException("Only one JsonSchema default annotation can be declared", annotated);
        }
        if (defaultAnnotations.isEmpty()) {
            return;
        }

        Annotation defaultAnnotation = defaultAnnotations.getFirst();
        TypeName annotationType = defaultAnnotation.typeName();
        if (annotationType.equals(SchemaTypes.JSON_SCHEMA_DEFAULT)) {
            defaultAnnotation.stringValue().ifPresent(value -> builder.set("default", value));
        } else if (annotationType.equals(SchemaTypes.JSON_SCHEMA_DEFAULT_INT)) {
            defaultAnnotation.intValue().ifPresent(value -> builder.set("default", value));
        } else if (annotationType.equals(SchemaTypes.JSON_SCHEMA_DEFAULT_LONG)) {
            defaultAnnotation.longValue().ifPresent(value -> builder.set("default", value));
        } else if (annotationType.equals(SchemaTypes.JSON_SCHEMA_DEFAULT_DOUBLE)) {
            defaultAnnotation.doubleValue().ifPresent(value -> builder.set("default", value));
        } else if (annotationType.equals(SchemaTypes.JSON_SCHEMA_DEFAULT_BOOLEAN)) {
            defaultAnnotation.booleanValue().ifPresent(value -> builder.set("default", value));
        } else {
            defaultAnnotation.stringValue()
                    .map(value -> parseDefaultJson(annotated, value))
                    .ifPresent(value -> builder.set("default", value));
        }
    }

    private static JsonValue parseDefaultJson(Annotated annotated, String value) {
        validateJsonNumbers(annotated, value);
        try {
            JsonParser parser = JsonParser.create(value);
            JsonValue result = parser.readJsonValue();
            if (!parser.hasNext()) {
                return result;
            }
            try {
                parser.nextToken();
            } catch (JsonException _) {
                return result;
            }
        } catch (JsonException e) {
            throw new CodegenException("JsonSchema.DefaultJson value must contain exactly one valid JSON value",
                                       e,
                                       annotated);
        }
        throw new CodegenException("JsonSchema.DefaultJson value must contain exactly one valid JSON value", annotated);
    }

    private static void validateJsonNumbers(Annotated annotated, String value) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
                continue;
            }
            if (character != '-' && (character < '0' || character > '9')) {
                continue;
            }
            int numberEnd = i + 1;
            while (numberEnd < value.length() && isJsonNumberCharacter(value.charAt(numberEnd))) {
                numberEnd++;
            }
            if (!JSON_NUMBER_PATTERN.matcher(value.substring(i, numberEnd)).matches()) {
                throw new CodegenException("JsonSchema.DefaultJson value contains an invalid JSON number", annotated);
            }
            i = numberEnd - 1;
        }
    }

    private static boolean isJsonNumberCharacter(char character) {
        return character >= '0' && character <= '9'
                || character == '-'
                || character == '+'
                || character == '.'
                || character == 'e'
                || character == 'E';
    }

    private static void processIntegerAnnotations(JsonObject.Builder builder, Annotated annotatedType, AtomicBoolean required) {
        processCommonAnnotations(builder, annotatedType, required);
        builder.set("type", "integer");
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_INTEGER_MINIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(it -> builder.set("minimum", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_INTEGER_MAXIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(it -> builder.set("maximum", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_INTEGER_MULTIPLE_OF)
                .flatMap(it -> it.longValue())
                .ifPresent(it -> builder.set("multipleOf", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_INTEGER_EXCLUSIVE_MINIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(it -> builder.set("exclusiveMinimum", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_INTEGER_EXCLUSIVE_MAXIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(it -> builder.set("exclusiveMaximum", it));
    }

    private static void processStringAnnotations(JsonObject.Builder builder, Annotated annotated, AtomicBoolean required) {
        builder.set("type", "string");
        processCommonAnnotations(builder, annotated, required);
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_STRING_MIN_LENGTH)
                .flatMap(it -> it.longValue())
                .ifPresent(it -> builder.set("minLength", it));
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_STRING_MAX_LENGTH)
                .flatMap(it -> it.longValue())
                .ifPresent(it -> builder.set("maxLength", it));
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_STRING_PATTERN)
                .flatMap(it -> it.stringValue())
                .ifPresent(it -> builder.set("pattern", it));
    }

    private static void processObject(JsonObject.Builder builder,
                                      TypeInfo annotatedType,
                                      CodegenContext ctx,
                                      AtomicBoolean required) {
        builder.set("type", "object");
        processObjectAnnotations(builder, annotatedType, required);
        Map<String, List<SchemaObjectProperty>> objectProperties = obtainObjectProperties(annotatedType);

        List<String> requiredProperties = new ArrayList<>();
        JsonObject.Builder newBuilder = JsonObject.builder();
        objectProperties.forEach((name, properties) -> {
            AtomicBoolean requiredProperty = new AtomicBoolean();
            processObjectElement(newBuilder, ctx, properties, name, requiredProperty);
            if (requiredProperty.get()) {
                requiredProperties.add(name);
            }
        });
        if (!objectProperties.isEmpty()) {
            builder.set("properties", newBuilder.build());
        }
        if (!requiredProperties.isEmpty()) {
            builder.setStrings("required", List.copyOf(requiredProperties));
        }
    }

    private static Map<String, List<SchemaObjectProperty>> obtainObjectProperties(TypeInfo annotatedType) {
        Map<String, List<SchemaObjectProperty>> properties = new HashMap<>();

        //Discover all setters
        List<TypedElementInfo> methods = annotatedType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(it -> it.parameterArguments().size() == 1)
                .filter(it -> it.elementName().length() > 3)
                .toList();

        for (TypedElementInfo method : methods) {
            if (method.hasAnnotation(SchemaTypes.JSON_SCHEMA_IGNORE)
                    || method.hasAnnotation(SchemaTypes.JSONB_TRANSIENT)) {
                continue;
            }
            String methodName = method.elementName();
            if (methodName.startsWith("set")
                    && Character.isUpperCase(methodName.charAt(3))) {
                String name = method.findAnnotation(SchemaTypes.JSON_SCHEMA_PROPERTY_NAME)
                        .or(() -> method.findAnnotation(SchemaTypes.JSONB_PROPERTY))
                        .flatMap(it -> it.stringValue())
                        .orElseGet(() -> {
                            if (methodName.length() > 4) {
                                return Character.toLowerCase(methodName.charAt(3))
                                        + methodName.substring(4);
                            } else {
                                return String.valueOf(Character.toLowerCase(methodName.charAt(3)));
                            }
                        });
                TypedElementInfo parameter = method.parameterArguments().getFirst();
                properties.computeIfAbsent(name, key -> new ArrayList<>())
                        .add(new SchemaObjectProperty(method, parameter.typeName()));
            }
        }

        //Check for a type creator
        Optional<TypedElementInfo> maybeCreator = annotatedType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates::isStatic)
                .filter(it -> it.hasAnnotation(SchemaTypes.JSONB_CREATOR))
                .findFirst();

        Set<String> creatorParamNames = maybeCreator.map(creator -> {
                    Set<String> creatorParams = new LinkedHashSet<>();
                    List<TypedElementInfo> parameterArguments = creator.parameterArguments();
                    for (TypedElementInfo parameter : parameterArguments) {
                        String name = parameter.findAnnotation(SchemaTypes.JSON_SCHEMA_PROPERTY_NAME)
                                .or(() -> parameter.findAnnotation(SchemaTypes.JSONB_PROPERTY))
                                .flatMap(it -> it.stringValue())
                                .orElse(parameter.elementName());
                        creatorParams.add(name);
                        if (properties.containsKey(name)) {
                            continue;
                        }
                        properties.computeIfAbsent(name, key -> new ArrayList<>())
                                .add(new SchemaObjectProperty(parameter, parameter.typeName()));
                    }
                    return creatorParams;
                })
                .orElse(Set.of());

        //Discover processable fields
        List<TypedElementInfo> fields = annotatedType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(not(ElementInfoPredicates::isStatic))
                .toList();

        for (TypedElementInfo field : fields) {
            String name = field.findAnnotation(SchemaTypes.JSON_SCHEMA_PROPERTY_NAME)
                    .or(() -> field.findAnnotation(SchemaTypes.JSONB_PROPERTY))
                    .flatMap(it -> it.stringValue())
                    .orElse(field.elementName());
            if (field.hasAnnotation(SchemaTypes.JSON_SCHEMA_IGNORE)
                    || field.hasAnnotation(SchemaTypes.JSONB_TRANSIENT)) {
                if (creatorParamNames.contains(name)) {
                    throw new CodegenException("Ignored set on the field '" + name
                                                       + "', but it is required as a creator parameter.",
                                               field);
                }
                properties.remove(name);
                continue;
            }
            if (properties.containsKey(name)) {
                //If property already exists, that means it has setter or was used in a creator,
                //just add the field for more possible configurations
                properties.get(name).addFirst(new SchemaObjectProperty(field, field.typeName()));
                continue;
            }
            if (annotatedType.kind() == ElementKind.RECORD && maybeCreator.isEmpty()) {
                //Type was record and had no creator. We can add all the record properties.
                //If creator was present, we would be limited to only a certain properties.
                properties.computeIfAbsent(name, key -> new ArrayList<>())
                        .addFirst(new SchemaObjectProperty(field, field.typeName()));
            } else if (field.accessModifier() != AccessModifier.PRIVATE) {
                properties.computeIfAbsent(name, key -> new ArrayList<>())
                        .addFirst(new SchemaObjectProperty(field, field.typeName()));
            }
        }
        return properties;
    }

    private static void processObjectElement(JsonObject.Builder builder,
                                             CodegenContext ctx,
                                             List<SchemaObjectProperty> properties,
                                             String name,
                                             AtomicBoolean required) {
        SchemaObjectProperty last = properties.getLast();
        TypeName lastElementTypeName = last.typeName();
        TypeName parameterTypeName = BOXED_TO_PRIMITIVE.getOrDefault(lastElementTypeName, lastElementTypeName);
        JsonObject.Builder newStructBuilder = JsonObject.builder();
        if (INTEGERS.contains(parameterTypeName)) {
            properties.forEach(it -> processIntegerAnnotations(newStructBuilder, it.annotated(), required));
        } else if (NUMBERS.contains(parameterTypeName)) {
            properties.forEach(it -> processNumberAnnotations(newStructBuilder, it.annotated(), required));
        } else if (parameterTypeName.primitive()) {
            if (parameterTypeName.equals(TypeNames.PRIMITIVE_BOOLEAN)) {
                newStructBuilder.set("type", "boolean");
                properties.forEach(it -> processCommonAnnotations(newStructBuilder, it.annotated(), required));
            } else {
                properties.forEach(it -> processStringAnnotations(newStructBuilder, it.annotated(), required));
            }
        } else if (parameterTypeName.equals(TypeNames.STRING)) {
            properties.forEach(it -> processStringAnnotations(newStructBuilder, it.annotated(), required));
        } else if (parameterTypeName.array()
                || parameterTypeName.isList()
                || parameterTypeName.isSet()) {
            properties.forEach(it -> processArray(newStructBuilder, ctx, it.annotated(), parameterTypeName, required));
        } else {
            boolean doNotInspect = properties.stream()
                    .anyMatch(it -> it.annotated().hasAnnotation(SchemaTypes.JSON_SCHEMA_DO_NOT_INSPECT));
            if (parameterTypeName.packageName().startsWith("java.")
                    || parameterTypeName.packageName().startsWith("javax.")
                    || doNotInspect) {
                //Do not inspect java and javax package classes
                //Only the annotations on the element should be processed
                properties.forEach(it -> processObjectAnnotations(newStructBuilder, it.annotated(), required));
            } else {
                TypeInfo typeInfo = ctx.typeInfo(parameterTypeName)
                        .orElseThrow(() -> new CodegenException("Could not process required type: " + parameterTypeName,
                                                                last.annotated()));
                processObject(newStructBuilder, typeInfo, ctx, required);
                properties.forEach(it -> {
                    //process annotations on the method/field so they override the defaults from the type
                    processObjectAnnotations(newStructBuilder, it.annotated(), required);
                });
            }
        }
        builder.set(name, newStructBuilder.build());
    }

    private static void processNumberAnnotations(JsonObject.Builder numberBuilder,
                                                 Annotated annotatedType,
                                                 AtomicBoolean required) {
        numberBuilder.set("type", "number");
        processCommonAnnotations(numberBuilder, annotatedType, required);
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_NUMBER_MINIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(it -> numberBuilder.set("minimum", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_NUMBER_MAXIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(it -> numberBuilder.set("maximum", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_NUMBER_MULTIPLE_OF)
                .flatMap(it -> it.doubleValue())
                .ifPresent(it -> numberBuilder.set("multipleOf", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_NUMBER_EXCLUSIVE_MINIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(it -> numberBuilder.set("exclusiveMinimum", it));
        annotatedType.findAnnotation(SchemaTypes.JSON_SCHEMA_NUMBER_EXCLUSIVE_MAXIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(it -> numberBuilder.set("exclusiveMaximum", it));
    }

    private static void processObjectAnnotations(JsonObject.Builder builder, Annotated annotated, AtomicBoolean required) {
        processCommonAnnotations(builder, annotated, required);
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_OBJECT_MIN_PROPERTIES)
                .flatMap(it -> it.intValue())
                .ifPresent(it -> builder.set("minProperties", it));
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_OBJECT_MAX_PROPERTIES)
                .flatMap(it -> it.intValue())
                .ifPresent(it -> builder.set("maxProperties", it));
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_OBJECT_ADDITIONAL_PROPERTIES)
                .flatMap(it -> it.booleanValue())
                .ifPresent(it -> builder.set("additionalProperties", it));
    }

    private static void processArray(JsonObject.Builder builder,
                                     CodegenContext ctx,
                                     Annotated element,
                                     TypeName elementTypeName,
                                     AtomicBoolean required) {
        builder.set("type", "array");
        processArrayAnnotations(builder, element, required);
        TypeName typeName = elementTypeName;
        if (typeName.array()) {
            typeName = typeName.componentType().orElse(TypeNames.OBJECT);
        } else if (typeName.isList() || typeName.isSet()) {
            typeName = typeName.typeArguments().getFirst();
        }
        JsonObject.Builder itemsBuilder = JsonObject.builder();
        TypeName finalTypeName = BOXED_TO_PRIMITIVE.getOrDefault(typeName, typeName);
        if (INTEGERS.contains(finalTypeName)) {
            processIntegerAnnotations(itemsBuilder, element, required);
        } else if (NUMBERS.contains(finalTypeName)) {
            processNumberAnnotations(itemsBuilder, element, required);
        } else if (finalTypeName.primitive()) {
            if (finalTypeName.equals(TypeNames.PRIMITIVE_BOOLEAN)) {
                processCommonAnnotations(itemsBuilder, element, required);
            } else {
                processStringAnnotations(itemsBuilder, element, required);
            }
        } else if (finalTypeName.equals(TypeNames.STRING)) {
            processStringAnnotations(itemsBuilder, element, required);
        } else if (finalTypeName.array()
                || finalTypeName.isList()
                || finalTypeName.isSet()) {
            processArray(itemsBuilder, ctx, element, finalTypeName, required);
        } else {
            if (finalTypeName.packageName().startsWith("java")
                    || element.hasAnnotation(SchemaTypes.JSON_SCHEMA_DO_NOT_INSPECT)) {
                //Do not inspect java and javax package classes
                //Only the annotations on the element should be processed
                processObjectAnnotations(itemsBuilder, element, required);
                return;
            }
            TypeInfo typeInfo = ctx.typeInfo(finalTypeName)
                    .orElseThrow(() -> new CodegenException("Could not process required type: " + finalTypeName,
                                                            element));
            processObject(itemsBuilder, typeInfo, ctx, required);
            processObjectAnnotations(itemsBuilder, element, required);
        }
    }

    private static void processArrayAnnotations(JsonObject.Builder arrayBuilder,
                                                Annotated annotated,
                                                AtomicBoolean required) {
        processCommonAnnotations(arrayBuilder, annotated, required);
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_ARRAY_MIN_ITEMS)
                .flatMap(it -> it.intValue())
                .ifPresent(it -> arrayBuilder.set("minItems", it));
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_ARRAY_MAX_ITEMS)
                .flatMap(it -> it.intValue())
                .ifPresent(it -> arrayBuilder.set("maxItems", it));
        annotated.findAnnotation(SchemaTypes.JSON_SCHEMA_ARRAY_UNIQUE_ITEMS)
                .flatMap(it -> it.booleanValue())
                .ifPresent(it -> arrayBuilder.set("uniqueItems", it));
    }

}
