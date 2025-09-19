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

package io.helidon.json.schema.codegen;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.json.schema.Schema;
import io.helidon.json.schema.SchemaArray;
import io.helidon.json.schema.SchemaInteger;
import io.helidon.json.schema.SchemaItem;
import io.helidon.json.schema.SchemaNumber;
import io.helidon.json.schema.SchemaObject;
import io.helidon.json.schema.SchemaString;

import static java.util.function.Predicate.not;

import static io.helidon.common.types.TypeNames.BOXED_BOOLEAN;
import static io.helidon.common.types.TypeNames.BOXED_BYTE;
import static io.helidon.common.types.TypeNames.BOXED_CHAR;
import static io.helidon.common.types.TypeNames.BOXED_DOUBLE;
import static io.helidon.common.types.TypeNames.BOXED_FLOAT;
import static io.helidon.common.types.TypeNames.BOXED_INT;
import static io.helidon.common.types.TypeNames.BOXED_LONG;
import static io.helidon.common.types.TypeNames.BOXED_SHORT;
import static io.helidon.common.types.TypeNames.BOXED_VOID;
import static io.helidon.common.types.TypeNames.OBJECT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_BOOLEAN;
import static io.helidon.common.types.TypeNames.PRIMITIVE_BYTE;
import static io.helidon.common.types.TypeNames.PRIMITIVE_CHAR;
import static io.helidon.common.types.TypeNames.PRIMITIVE_DOUBLE;
import static io.helidon.common.types.TypeNames.PRIMITIVE_FLOAT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_INT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_LONG;
import static io.helidon.common.types.TypeNames.PRIMITIVE_SHORT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_VOID;

record SchemaInfo(TypeName generatedSchema, Schema schema) {

    private static final Map<TypeName, TypeName> BOXED_TO_PRIMITIVE = Map.of(
            BOXED_BOOLEAN, PRIMITIVE_BOOLEAN,
            BOXED_BYTE, PRIMITIVE_BYTE,
            BOXED_SHORT, PRIMITIVE_SHORT,
            BOXED_INT, PRIMITIVE_INT,
            BOXED_LONG, PRIMITIVE_LONG,
            BOXED_CHAR, PRIMITIVE_CHAR,
            BOXED_FLOAT, PRIMITIVE_FLOAT,
            BOXED_DOUBLE, PRIMITIVE_DOUBLE,
            BOXED_VOID, PRIMITIVE_VOID
    );

    private static final Set<TypeName> INTEGERS = Set.of(PRIMITIVE_BYTE,
                                                         PRIMITIVE_SHORT,
                                                         PRIMITIVE_INT,
                                                         PRIMITIVE_LONG,
                                                         Types.BIG_INTEGER);

    private static final Set<TypeName> NUMBERS = Set.of(PRIMITIVE_FLOAT,
                                                        PRIMITIVE_DOUBLE,
                                                        Types.BIG_DECIMAL,
                                                        Types.NUMBER);

    public static SchemaInfo create(TypeInfo annotatedType, CodegenContext ctx) {
        TypeName annotatedTypeName = annotatedType.typeName();
        TypeName generatedTypeName = TypeName.builder()
                .from(annotatedTypeName)
                .className(annotatedTypeName.className() + "__JsonSchema")
                .build();

        Schema.Builder builder = Schema.builder();
        annotatedType.findAnnotation(Types.JSON_SCHEMA_ID)
                .flatMap(it -> it.stringValue())
                .map(URI::create)
                .ifPresent(builder::id);
        builder.rootObject(objectBuilder -> processObject(objectBuilder, annotatedType, ctx));
        return new SchemaInfo(generatedTypeName, builder.build());
    }

    private static void processCommonAnnotations(SchemaItem.BuilderBase<?, ?> builderBase, Annotated annotatedType) {
        annotatedType.findAnnotation(Types.JSON_SCHEMA_TITLE)
                .flatMap(it -> it.stringValue())
                .ifPresent(builderBase::title);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_DESCRIPTION)
                .flatMap(it -> it.stringValue())
                .ifPresent(builderBase::description);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_REQUIRED).ifPresent(it -> builderBase.required(true));
    }

    private static void processIntegerAnnotations(SchemaInteger.Builder integerBuilder, Annotated annotatedType) {
        processCommonAnnotations(integerBuilder, annotatedType);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_INTEGER_MINIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(integerBuilder::minimum);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_INTEGER_MAXIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(integerBuilder::maximum);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_INTEGER_MULTIPLE_OF)
                .flatMap(it -> it.longValue())
                .ifPresent(integerBuilder::multipleOf);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_INTEGER_EXCLUSIVE_MINIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(integerBuilder::exclusiveMinimum);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_INTEGER_EXCLUSIVE_MAXIMUM)
                .flatMap(it -> it.longValue())
                .ifPresent(integerBuilder::exclusiveMaximum);
    }

    private static void processStringAnnotations(SchemaString.Builder builder, Annotated annotated) {
        processCommonAnnotations(builder, annotated);
        annotated.findAnnotation(Types.JSON_SCHEMA_STRING_MIN_LENGTH)
                .flatMap(it -> it.longValue())
                .ifPresent(builder::minLength);
        annotated.findAnnotation(Types.JSON_SCHEMA_STRING_MAX_LENGTH)
                .flatMap(it -> it.longValue())
                .ifPresent(builder::maxLength);
        annotated.findAnnotation(Types.JSON_SCHEMA_STRING_PATTERN)
                .flatMap(it -> it.stringValue())
                .ifPresent(builder::pattern);
    }

    private static void processObject(SchemaObject.Builder builder,
                                      TypeInfo annotatedType,
                                      CodegenContext ctx) {
        processObjectAnnotations(builder, annotatedType);
        Map<String, List<SchemaObjectProperty>> objectProperties = obtainObjectProperties(annotatedType);

        objectProperties.forEach((name, properties) -> {
            processObjectElement(builder, ctx, properties, name);
        });
    }

    private static Map<String, List<SchemaObjectProperty>> obtainObjectProperties(TypeInfo annotatedType) {
        Map<String, List<SchemaObjectProperty>> properties = new HashMap<>();

        //Discover all setters
        List<TypedElementInfo> methods = annotatedType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .toList();

        for (TypedElementInfo method : methods) {
            if (method.hasAnnotation(Types.JSON_SCHEMA_IGNORE)
                    || method.hasAnnotation(Types.JSONB_TRANSIENT)) {
                continue;
            }
            String methodName = method.elementName();
            if (methodName.startsWith("set")
                    && methodName.length() > 3
                    && Character.isUpperCase(methodName.charAt(3))
                    && method.parameterArguments().size() == 1) {
                String name = method.findAnnotation(Types.JSON_SCHEMA_PROPERTY_NAME)
                        .or(() -> method.findAnnotation(Types.JSONB_PROPERTY))
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
                .filter(it -> it.hasAnnotation(Types.JSONB_CREATOR))
                .findFirst();

        maybeCreator.ifPresent(creator -> {
            List<TypedElementInfo> parameterArguments = creator.parameterArguments();
            for (TypedElementInfo parameter : parameterArguments) {
                String name = parameter.findAnnotation(Types.JSON_SCHEMA_PROPERTY_NAME)
                        .or(() -> parameter.findAnnotation(Types.JSONB_PROPERTY))
                        .flatMap(it -> it.stringValue())
                        .orElse(parameter.elementName());
                if (properties.containsKey(name)) {
                    continue;
                }
                properties.computeIfAbsent(name, key -> new ArrayList<>())
                        .add(new SchemaObjectProperty(parameter, parameter.typeName()));
            }
        });

        //Discover processable fields
        List<TypedElementInfo> fields = annotatedType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(not(ElementInfoPredicates::isStatic))
                .toList();

        for (TypedElementInfo field : fields) {
            String name = field.findAnnotation(Types.JSON_SCHEMA_PROPERTY_NAME)
                    .or(() -> field.findAnnotation(Types.JSONB_PROPERTY))
                    .flatMap(it -> it.stringValue())
                    .orElse(field.elementName());
            if (field.hasAnnotation(Types.JSON_SCHEMA_IGNORE)
                    || field.hasAnnotation(Types.JSONB_TRANSIENT)) {
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

    private static void processObjectElement(SchemaObject.Builder builder,
                                             CodegenContext ctx,
                                             List<SchemaObjectProperty> properties,
                                             String name) {
        SchemaObjectProperty last = properties.getLast();
        TypeName lastElementTypeName = last.typeName();
        TypeName parameterTypeName = BOXED_TO_PRIMITIVE.getOrDefault(lastElementTypeName, lastElementTypeName);
        if (INTEGERS.contains(parameterTypeName)) {
            builder.addIntegerProperty(name, integerBuilder -> properties.forEach(
                    it -> processIntegerAnnotations(integerBuilder, it.annotated())));
        } else if (NUMBERS.contains(parameterTypeName)) {
            builder.addNumberProperty(name, numberBuilder -> properties.forEach(
                    it -> processNumberAnnotations(numberBuilder, it.annotated())));
        } else if (parameterTypeName.primitive()) {
            if (parameterTypeName.equals(TypeNames.PRIMITIVE_BOOLEAN)) {
                builder.addBooleanProperty(name, booleanBuilder -> properties.forEach(
                        it -> processCommonAnnotations(booleanBuilder, it.annotated())));
            } else {
                builder.addStringProperty(name, stringBuilder -> properties.forEach(
                        it -> processStringAnnotations(stringBuilder, it.annotated())));
            }
        } else if (parameterTypeName.equals(TypeNames.STRING)) {
            builder.addStringProperty(name, stringBuilder -> properties.forEach(
                    it -> processStringAnnotations(stringBuilder, it.annotated())));
        } else if (parameterTypeName.array()
                || parameterTypeName.isList()
                || parameterTypeName.isSet()) {
            builder.addArrayProperty(name, arrayBuilder -> properties.forEach(
                    it -> processArray(arrayBuilder, ctx, it.annotated(), parameterTypeName)));
        } else {
            boolean doNotInspect = properties.stream()
                    .anyMatch(it -> it.annotated().hasAnnotation(Types.JSON_SCHEMA_DO_NOT_INSPECT));
            if (parameterTypeName.packageName().startsWith("java.")
                    || parameterTypeName.packageName().startsWith("javax.")
                    || doNotInspect) {
                //Do not inspect java and javax package classes
                //Only the annotations on the element should be processed
                builder.addObjectProperty(name, objectBuilder -> properties.forEach(
                        it -> processObjectAnnotations(objectBuilder, it.annotated())));
                return;
            }
            TypeInfo typeInfo = ctx.typeInfo(parameterTypeName)
                    .orElseThrow(() -> new IllegalStateException("Could not process required type: " + parameterTypeName));

            builder.addObjectProperty(name, objectBuilder -> {
                processObject(objectBuilder, typeInfo, ctx);
                properties.forEach(it -> {
                    //process annotations on the method so they override the defaults from the type
                    processObjectAnnotations(objectBuilder, it.annotated());
                });
            });
        }
    }

    private static void processNumberAnnotations(SchemaNumber.Builder numberBuilder, Annotated annotatedType) {
        processCommonAnnotations(numberBuilder, annotatedType);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_NUMBER_MINIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(numberBuilder::minimum);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_NUMBER_MAXIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(numberBuilder::maximum);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_NUMBER_MULTIPLE_OF)
                .flatMap(it -> it.doubleValue())
                .ifPresent(numberBuilder::multipleOf);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_NUMBER_EXCLUSIVE_MINIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(numberBuilder::exclusiveMinimum);
        annotatedType.findAnnotation(Types.JSON_SCHEMA_NUMBER_EXCLUSIVE_MAXIMUM)
                .flatMap(it -> it.doubleValue())
                .ifPresent(numberBuilder::exclusiveMaximum);
    }

    private static void processObjectAnnotations(SchemaObject.Builder builder, Annotated annotated) {
        processCommonAnnotations(builder, annotated);
        annotated.findAnnotation(Types.JSON_SCHEMA_OBJECT_MIN_PROPERTIES)
                .flatMap(it -> it.intValue())
                .ifPresent(builder::minProperties);
        annotated.findAnnotation(Types.JSON_SCHEMA_OBJECT_MAX_PROPERTIES)
                .flatMap(it -> it.intValue())
                .ifPresent(builder::maxProperties);
        annotated.findAnnotation(Types.JSON_SCHEMA_OBJECT_ADDITIONAL_PROPERTIES)
                .flatMap(it -> it.booleanValue())
                .ifPresent(builder::additionalProperties);
    }

    private static void processArray(SchemaArray.Builder builder,
                                     CodegenContext ctx,
                                     Annotated element,
                                     TypeName elementTypeName) {
        processArrayAnnotations(builder, ctx, element);
        TypeName typeName = elementTypeName;
        if (typeName.array()) {
            typeName = typeName.componentType().orElse(OBJECT);
        } else if (typeName.isList() || typeName.isSet()) {
            typeName = typeName.typeArguments().getFirst();
        }
        TypeName finalTypeName = BOXED_TO_PRIMITIVE.getOrDefault(typeName, typeName);
        if (INTEGERS.contains(finalTypeName)) {
            builder.itemsInteger(integerBuilder -> processIntegerAnnotations(integerBuilder, element));
        } else if (NUMBERS.contains(finalTypeName)) {
            builder.itemsNumber(numberBuilder -> processNumberAnnotations(numberBuilder, element));
        } else if (finalTypeName.primitive()) {
            if (finalTypeName.equals(TypeNames.PRIMITIVE_BOOLEAN)) {
                builder.itemsBoolean(booleanBuilder -> processCommonAnnotations(booleanBuilder, element));
            } else {
                builder.itemsString(stringBuilder -> processStringAnnotations(stringBuilder, element));
            }
        } else if (finalTypeName.equals(TypeNames.STRING)) {
            builder.itemsString(stringBuilder -> processStringAnnotations(stringBuilder, element));
        } else if (finalTypeName.array()
                || finalTypeName.isList()
                || finalTypeName.isSet()) {
            builder.itemsArray(arrayBuilder -> processArray(arrayBuilder, ctx, element, finalTypeName));
        } else {
            if (finalTypeName.packageName().startsWith("java")
                    || element.hasAnnotation(Types.JSON_SCHEMA_DO_NOT_INSPECT)) {
                //Do not inspect java and javax package classes
                //Only the annotations on the element should be processed
                builder.itemsObject(objectBuilder -> processObjectAnnotations(objectBuilder, element));
                return;
            }
            TypeInfo typeInfo = ctx.typeInfo(finalTypeName)
                    .orElseThrow(() -> new IllegalStateException("Could not process required type: " + finalTypeName));

            builder.itemsObject(objectBuilder -> processObject(objectBuilder, typeInfo, ctx));
        }
    }

    private static void processArrayAnnotations(SchemaArray.Builder arrayBuilder,
                                                CodegenContext ctx,
                                                Annotated annotated) {
        processCommonAnnotations(arrayBuilder, annotated);
        annotated.findAnnotation(Types.JSON_SCHEMA_ARRAY_MIN_ITEMS)
                .flatMap(it -> it.intValue())
                .ifPresent(arrayBuilder::minItems);
        annotated.findAnnotation(Types.JSON_SCHEMA_ARRAY_MAX_ITEMS)
                .flatMap(it -> it.intValue())
                .ifPresent(arrayBuilder::maxItems);
        annotated.findAnnotation(Types.JSON_SCHEMA_ARRAY_UNIQUE_ITEMS)
                .flatMap(it -> it.booleanValue())
                .ifPresent(arrayBuilder::uniqueItems);
    }

}
