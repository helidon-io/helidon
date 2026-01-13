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

package io.helidon.json.codegen;

import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.Executable;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.json.codegen.ConvertedTypeInfo.needsResolving;
import static io.helidon.json.codegen.JsonTypes.BYTES;
import static java.util.function.Predicate.not;

class JsonConverterGenerator {

    static final String CONFIGURE_PARAM = "jsonBindingConfigurator";
    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;
    private static final String PROPERTY_NAME_SUFFIX = "_";
    private static final String MISSING_SUFFIX = "_missing";
    private static final Supplier<?> DEFAULT_TYPE_VALUE = () -> null;
    private static final Map<TypeName, Supplier<?>> DEFAULT_TYPE_VALUES = Map.of(
            TypeNames.PRIMITIVE_BOOLEAN, () -> false,
            TypeNames.PRIMITIVE_BYTE, () -> 0,
            TypeNames.PRIMITIVE_SHORT, () -> 0,
            TypeNames.PRIMITIVE_INT, () -> 0,
            TypeNames.PRIMITIVE_LONG, () -> 0,
            TypeNames.PRIMITIVE_FLOAT, () -> "0.0F",
            TypeNames.PRIMITIVE_DOUBLE, () -> "0.0"
    );
    private static final String WRITE_NULLS = "writeNulls";

    private JsonConverterGenerator() {
    }

    static void generateConverter(ClassBase.Builder<?, ?> classBuilder, ConvertedTypeInfo converterInfo, boolean factory) {
        TypeName converterInterfaceType = TypeName.builder()
                .from(JsonTypes.JSON_CONVERTER_TYPE)
                .addTypeArgument(converterInfo.wildcardsGenerics())
                .build();

        Map<String, TypeToConfigure> toConfigure = new HashMap<>();
        classBuilder.name(converterInfo.converterType().className())
                .addInterface(converterInterfaceType)
                .javadoc(Javadoc.builder()
                                 .add("Json converter for {@link " + converterInfo.originalType().fqName() + "}.")
                                 .build())
                .addMethod(method -> generateToJsonMethod(classBuilder,
                                                          method,
                                                          converterInfo,
                                                          toConfigure))
                .addMethod(method -> generateFromJsonMethod(classBuilder,
                                                            method,
                                                            converterInfo,
                                                            toConfigure));

        if (factory) {
            classBuilder.addMethod(method -> addConfigurationFactory(method, toConfigure, converterInfo));
            classBuilder.addMethod(method -> addTypeMethodFactory(method, converterInfo));
        } else {
            classBuilder.addMethod(method -> addConfigurationMethod(method, toConfigure));
            classBuilder.addMethod(method -> addTypeMethod(method, converterInfo));
        }
    }

    private static void addConfigurationMethod(Method.Builder method, Map<String, TypeToConfigure> toConfigure) {
        method.name("configure")
                .addAnnotation(Annotation.create(Override.class))
                .addParameter(param -> param.type(JsonTypes.JSON_BINDING_CONFIGURATOR).name(CONFIGURE_PARAM));

        initializeNoRuntimeResolving(method, toConfigure);
    }

    private static void addConfigurationFactory(Method.Builder method,
                                                Map<String, TypeToConfigure> toConfigure,
                                                ConvertedTypeInfo convertedTypeInfo) {
        method.name("configure")
                .addAnnotation(Annotation.create(Override.class))
                .addParameter(builder -> builder.type(JsonTypes.JSON_BINDING_CONFIGURATOR).name(CONFIGURE_PARAM));

        initializeNoRuntimeResolving(method, toConfigure);

        List<TypeToConfigure> needsRuntimeResolving = toConfigure.values()
                .stream()
                .filter(it -> needsResolving(it.resolved))
                .toList();

        method.addContent("if (type instanceof ")
                .addContent(ParameterizedType.class)
                .addContentLine(" parameterizedType) {");

        Map<String, Consumer<Method.Builder>> createdTypeSetters = new HashMap<>();
        MethodNameCounter counter = new MethodNameCounter();
        Map<TypeName, Integer> paramIndexes = convertedTypeInfo.genericParamsWithIndexes();
        for (TypeToConfigure typeToConfigure : needsRuntimeResolving) {
            String fieldName = typeToConfigure.fieldName();
            TypeName typeName = typeToConfigure.resolved;
            String obtainMethod = typeToConfigure.mode.method;
            if (typeName.generic()) {
                int index = paramIndexes.get(typeName);
                method.addContent(fieldName + " = " + CONFIGURE_PARAM + "." + obtainMethod + "(")
                        .addContentLine("parameterizedType.getActualTypeArguments()[" + index + "]);");
            } else {
                Consumer<Method.Builder> builderConsumer;
                if (createdTypeSetters.containsKey(typeName.resolvedName())) {
                    builderConsumer = createdTypeSetters.get(typeName.resolvedName());
                } else {
                    builderConsumer = constructComplexGenericType(method,
                                                                  typeName,
                                                                  createdTypeSetters,
                                                                  counter,
                                                                  convertedTypeInfo);
                    createdTypeSetters.put(typeName.resolvedName(), builderConsumer);
                }
                method.addContent(fieldName + " = " + CONFIGURE_PARAM + "." + obtainMethod + "(");
                builderConsumer.accept(method);
                method.addContentLine(");");
            }
        }
        method.addContent("}").addContentLine(" else {");
        for (TypeToConfigure typeToConfigure : needsRuntimeResolving) {
            String fieldName = typeToConfigure.fieldName();
            TypeName typeName = typeToConfigure.resolved;
            String obtainMethod = typeToConfigure.mode.method;
            if (typeName.typeArguments().isEmpty()) {
                method.addContent(fieldName + " = (")
                        .addContent(typeToConfigure.fieldType)
                        .addContent(") " + CONFIGURE_PARAM + "." + obtainMethod + "(")
                        .addContent(Object.class)
                        .addContentLine(".class);");
            } else {
                method.addContent(fieldName + " = " + CONFIGURE_PARAM + "." + obtainMethod + "(")
                        .addContent("new ").addContent(TypeNames.GENERIC_TYPE).addContent("<");
                buildSimpleGenericTypeWithObject(method, typeName);
                method.addContentLine(">() {});");
            }
        }
        method.addContentLine("}");
    }

    private static void buildSimpleGenericTypeWithObject(Method.Builder method, TypeName typeName) {
        if (typeName.typeArguments().isEmpty()) {
            //We have no more generics available
            method.addContent(typeName);
        } else {
            boolean first = true;
            method.addContent(typeName.genericTypeName()).addContent("<");
            for (TypeName typeArgument : typeName.typeArguments()) {
                if (first) {
                    first = false;
                } else {
                    method.addContent(",");
                }
                buildSimpleGenericTypeWithObject(method, typeArgument);
            }
            method.addContent(">");
        }
    }

    private static Consumer<Method.Builder> constructComplexGenericType(Method.Builder method,
                                                                        TypeName typeName,
                                                                        Map<String, Consumer<Method.Builder>> createdTypeSetters,
                                                                        MethodNameCounter counter,
                                                                        ConvertedTypeInfo convertedTypeInfo) {
        Map<TypeName, Integer> paramIndexes = convertedTypeInfo.genericParamsWithIndexes();
        if (typeName.typeArguments().isEmpty()) {
            if (needsResolving(typeName)) {
                int index = paramIndexes.get(typeName);
                return builder -> builder.addContent(TypeNames.GENERIC_TYPE)
                        .addContent(".create(parameterizedType.getActualTypeArguments()[" + index + "])");
            } else {
                return builder -> builder.addContent(TypeNames.GENERIC_TYPE)
                        .addContent(".create(").addContent(typeName).addContent(")");
            }
        } else {
            List<Consumer<Method.Builder>> parameterValueSetters = new ArrayList<>();
            for (TypeName typeArgument : typeName.typeArguments()) {
                if (createdTypeSetters.containsKey(typeArgument.resolvedName())) {
                    parameterValueSetters.add(createdTypeSetters.get(typeArgument.resolvedName()));
                } else {
                    Consumer<Method.Builder> parameterValue = constructComplexGenericType(method,
                                                                                          typeArgument,
                                                                                          createdTypeSetters,
                                                                                          counter,
                                                                                          convertedTypeInfo);
                    parameterValueSetters.add(parameterValue);
                    createdTypeSetters.putIfAbsent(typeArgument.resolvedName(), parameterValue);
                }
            }
            String variableName = "genericType" + counter.count++;
            method.addContent("var " + variableName + " = ")
                    .addContent(TypeNames.GENERIC_TYPE)
                    .addContent(".<").addContent(typeName).addContentLine(">builder()")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".baseType(").addContent(typeName.genericTypeName()).addContentLine(".class)");
            for (Consumer<Method.Builder> parameterValue : parameterValueSetters) {
                method.addContent(".addGenericParameter(");
                parameterValue.accept(method);
                method.addContentLine(")");
            }
            method.addContentLine(".build();")
                    .decreaseContentPadding()
                    .decreaseContentPadding();

            return builder -> builder.addContent(variableName);
        }
    }

    private static void initializeNoRuntimeResolving(Executable.Builder<?, ?> method, Map<String, TypeToConfigure> toConfigure) {
        List<TypeToConfigure> doNotNeedRuntimeResolving = toConfigure.values()
                .stream()
                .filter(not(it -> needsResolving(it.resolved)))
                .toList();

        for (TypeToConfigure typeToConfigure : doNotNeedRuntimeResolving) {
            TypeName typeName = typeToConfigure.original;
            String fieldName = typeToConfigure.fieldName();
            String obtainMethod = typeToConfigure.mode.method;
            if (typeName.typeArguments().isEmpty()) {
                method.addContent(fieldName + " = " + CONFIGURE_PARAM + "." + obtainMethod + "(")
                        .addContent(typeName)
                        .addContentLine(".class);");
            } else {
                method.addContent(fieldName + " = " + CONFIGURE_PARAM + "." + obtainMethod + "(new ")
                        .addContent(TypeNames.GENERIC_TYPE)
                        .addContent("<")
                        .addContent(typeName)
                        .addContentLine(">() {});");
            }
        }
    }

    private static void generateToJsonMethod(ClassBase.Builder<?, ?> classBuilder,
                                             Method.Builder method,
                                             ConvertedTypeInfo converterInfo,
                                             Map<String, TypeToConfigure> toConfigure) {
        method.name("serialize")
                .addParameter(param -> param.name("generator").type(JsonTypes.JSON_GENERATOR))
                .addParameter(param -> param.name("instance").type(converterInfo.wildcardsGenerics()))
                .addParameter(param -> param.name(WRITE_NULLS).type(boolean.class))
                .addAnnotation(Annotation.create(Override.class))
                .addContentLine("generator.writeObjectStart();");
        List<JsonProperty> jsonProperties = converterInfo.jsonProperties()
                .values()
                .stream()
                .filter(JsonConverterGenerator::usableForSerialization)
                .sorted((o1, o2) -> converterInfo.orderedProperties()
                        .compare(o1.serializationName().orElseThrow(),
                                 o2.serializationName().orElseThrow()))
                .toList();

        Set<String> createdSerializers = new HashSet<>();
        for (JsonProperty jsonProperty : jsonProperties) {
            String fieldName = jsonProperty.serializer()
                    .map(serializer -> {
                        String constantName = constantName(jsonProperty.serializationName().orElseThrow()) + "_SERIALIZER";
                        classBuilder.addField(field -> field.name(constantName)
                                .type(serializer)
                                .isStatic(true)
                                .isFinal(true)
                                .addContent("new ").addContent(serializer).addContent("()"));
                        return constantName;
                    })
                    .orElseGet(() -> {
                        TypeName type = jsonProperty.serializationType().orElseThrow();
                        TypeName resolved = type.boxed();
                        String fn;
                        if (!resolved.typeArguments().isEmpty()) {
                            fn = "serializer" + ensureUpperStart(jsonProperty.serializationName().orElseThrow());
                        } else {
                            fn = "serializer" + ensureUpperStart(type);
                        }
                        if (!createdSerializers.contains(fn)) {
                            TypeName serializerArgument = resolved.generic() ? TypeNames.OBJECT : resolved;
                            createdSerializers.add(fn);
                            TypeName converterType = TypeName.builder()
                                    .from(JsonTypes.JSON_SERIALIZER_TYPE)
                                    .addTypeArgument(serializerArgument)
                                    .build();
                            classBuilder.addField(fieldBuilder -> fieldBuilder.name(fn)
                                    .isVolatile(true)
                                    .type(converterType));
                            toConfigure.putIfAbsent(fn,
                                                    new TypeToConfigure(TypeConfigMode.SERIALIZATION,
                                                                        fn,
                                                                        resolved,
                                                                        type,
                                                                        converterType));
                        }
                        return fn;
                    });

            String accessor = jsonProperty.getterName()
                    .filter(getterName -> !jsonProperty.getterIgnored().orElse(false))
                    .map(getterName -> getterName + "()")
                    .or(jsonProperty::fieldName)
                    .orElseThrow();

            String key = jsonProperty.serializationName().orElseThrow();
            method.addContent(JsonTypes.JSON_SERIALIZERS)
                    .addContentLine(".serialize(generator, " + fieldName + ", "
                                            + "instance." + accessor + ", "
                                            + "\"" + key + "\", "
                                            + jsonProperty.nullable() + ");");
        }
        method.addContentLine("generator.writeObjectEnd();");
    }

    private static boolean usableForSerialization(JsonProperty property) {
        if ((property.getterIgnored().isPresent() && property.getterIgnored().get()) || property.getterName().isEmpty()) {
            return !property.fieldIgnored() && property.directFieldRead();
        } else if (property.fieldIgnored()) {
            return false; //Field is ignored
        }
        return property.getterName().isPresent();
    }

    /**
     * Generates the deserialize method that parses JSON object into the target type.
     * Handles different creation patterns: constructors, factory methods, builders.
     */
    private static void generateFromJsonMethod(ClassBase.Builder<?, ?> classBuilder,
                                               Method.Builder method,
                                               ConvertedTypeInfo converterInfo,
                                               Map<String, TypeToConfigure> toConfigure) {
        CreatorInfo creatorInfo = converterInfo.creatorInfo();
        ElementKind creatorKind = creatorInfo.creatorKind();
        boolean hasCreator = creatorKind != null && !creatorInfo.parameters().isEmpty();
        boolean hasBuilder = converterInfo.builderInfo().isPresent();
        List<JsonProperty> jsonProperties = converterInfo.jsonProperties()
                .values()
                .stream()
                .filter(JsonConverterGenerator::usableForDeserialization)
                .toList();

        method.name("deserialize")
                .returnType(converterInfo.wildcardsGenerics())
                .addParameter(param -> param.name("parser").type(JsonTypes.JSON_PARSER))
                .addAnnotation(Annotation.create(Override.class))
                .addContent(byte.class).addContentLine(" lastByte = parser.currentByte();")
                .addContent("if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".BRACE_OPEN_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected '{' to start an object\", lastByte);")
                .addContentLine("}")
                .addContentLine("lastByte = parser.nextToken();");
        createPreProcessingVariables(method, converterInfo, jsonProperties, hasCreator, creatorKind, creatorInfo, hasBuilder);
        earlyReturnForEmptyObjects(method, converterInfo, jsonProperties, hasCreator, creatorKind, creatorInfo, hasBuilder);
        propertyProcessing(classBuilder, method, converterInfo, toConfigure, jsonProperties, hasCreator, hasBuilder);
        createFinalInstanceCreation(method, converterInfo, jsonProperties, hasCreator, creatorKind, creatorInfo, hasBuilder);
    }

    private static boolean usableForDeserialization(JsonProperty property) {
        if ((property.setterIgnored().isPresent() && property.setterIgnored().get()) || property.setterName().isEmpty()) {
            if (property.fieldIgnored()) {
                return false; //Field is ignored
            }
            if (property.usedInBuilder() || property.usedInCreator()) {
                return true;
            }
            return property.directFieldWrite();
        } else if (property.fieldIgnored()) {
            return false; //Field is ignored
        }
        return property.setterName().isPresent();
    }

    private static void createFinalInstanceCreation(Method.Builder method,
                                                    ConvertedTypeInfo converterInfo,
                                                    List<JsonProperty> jsonProperties,
                                                    boolean hasCreator,
                                                    ElementKind creatorKind,
                                                    CreatorInfo creatorInfo,
                                                    boolean hasBuilder) {
        jsonProperties.stream()
                .filter(JsonProperty::required)
                .forEach(property -> {
                    String name = JsonConverterGenerator.convertToMissingName(property);
                    method.addContentLine("if (" + name + ") {")
                            .addContent("throw parser.createException(\"Property \\\"")
                            .addContent(property.deserializationName().orElseThrow())
                            .addContentLine("\\\" was required to be present in the JSON, but was missing.\");")
                            .addContentLine("}");
                });
        if (hasCreator) {
            TypeName originalType = converterInfo.objectsGenerics();
            if (creatorKind == ElementKind.METHOD) {
                method.addContent(originalType).addContent(" instance = ")
                        .addContent(originalType).addContent("." + creatorInfo.method() + "(");
            } else {
                method.addContent(originalType).addContent(" instance = new ")
                        .addContent(originalType).addContent("(");
            }
            String properties = jsonProperties.stream()
                    .filter(JsonProperty::usedInCreator)
                    .map(property -> property.deserializationName().orElseThrow() + PROPERTY_NAME_SUFFIX)
                    .collect(Collectors.joining(", "));
            method.addContent(properties).addContentLine(");");
            for (JsonProperty property : jsonProperties) {
                if (!property.usedInCreator()) {
                    if (property.directFieldWrite()) {
                        method.addContentLine("instance." + property.fieldName().orElseThrow() + " = "
                                                      + property.deserializationName()
                                .orElseThrow() + PROPERTY_NAME_SUFFIX + ";");
                    } else {
                        method.addContentLine("instance." + property.setterName().orElseThrow() + "("
                                                      + property.deserializationName()
                                .orElseThrow() + PROPERTY_NAME_SUFFIX + ");");
                    }
                }
            }
        } else if (hasBuilder) {
            BuilderInfo builderInfo = converterInfo.builderInfo().get();
            method.addContent(converterInfo.objectsGenerics())
                    .addContent(" instance = builder.")
                    .addContent(builderInfo.buildMethodName())
                    .addContentLine("();");
            for (JsonProperty property : jsonProperties) {
                if (property.usedInBuilder()) {
                    //This property is handled by the builder
                    continue;
                }
                if (property.directFieldWrite()) {
                    method.addContentLine("instance." + property.fieldName().orElseThrow() + " = "
                                                  + property.deserializationName()
                            .orElseThrow() + PROPERTY_NAME_SUFFIX + ";");
                } else {
                    method.addContentLine("instance." + property.setterName().orElseThrow() + "("
                                                  + property.deserializationName()
                            .orElseThrow() + PROPERTY_NAME_SUFFIX + ");");
                }
            }
        }
        method.addContentLine("return instance;");
    }

    private static void propertyProcessing(ClassBase.Builder<?, ?> classBuilder,
                                           Method.Builder method,
                                           ConvertedTypeInfo converterInfo,
                                           Map<String, TypeToConfigure> toConfigure,
                                           List<JsonProperty> jsonProperties,
                                           boolean hasCreator,
                                           boolean hasBuilder) {
        boolean hasProperties = !jsonProperties.isEmpty();

        method.addContentLine("while(true) {")
                .addContent("if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".DOUBLE_QUOTE_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected '\\\"' as a key start\", lastByte);")
                .addContentLine("}");
        if (hasProperties) {
            method.addContent(int.class).addContentLine(" hash = parser.readStringAsHash();");
        } else {
            method.addContentLine("parser.skip();");
        }
        method.addContentLine("lastByte = parser.nextToken();")
                .addContent("if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".COLON_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected ':' to separate key and value\", lastByte);")
                .addContentLine("}")
                .addContentLine("parser.nextToken();");
        if (hasProperties) {
            boolean switchUsed = jsonProperties.size() > 9;
            if (switchUsed) {
                method.addContentLine("switch(hash) {");
            }
            Map<Integer, List<JsonProperty>> hashes = jsonProperties.stream()
                    .collect(Collectors.groupingBy(jsonProperty ->
                                                           calculateNameHash(jsonProperty.deserializationName().orElseThrow())));
            Set<String> processedTypes = new HashSet<>(); //Used to identify already configured type deserializers
            boolean first = true;
            for (Map.Entry<Integer, List<JsonProperty>> entry : hashes.entrySet()) {
                if (entry.getValue().size() > 1) {
                    throw new UnsupportedOperationException("Naming collision, not implemented yet");
                } else {
                    JsonProperty jsonProperty = entry.getValue().getFirst();
                    String constantName = constantName(jsonProperty.deserializationName().orElseThrow());
                    classBuilder.addField(builder -> builder.isFinal(true)
                            .isStatic(true)
                            .type(int.class)
                            .name(constantName)
                            .defaultValue(String.valueOf(entry.getKey())));
                    if (switchUsed) {
                        method.addContentLine("case " + constantName + ":");
                        method.increaseContentPadding();
                    } else if (first) {
                        method.addContentLine("if (hash == " + constantName + ") {");
                        first = false;
                    } else {
                        method.addContentLine(" else if (hash == " + constantName + ") {");
                    }
                    addTypeHandling(jsonProperty,
                                    method,
                                    classBuilder,
                                    hasCreator,
                                    hasBuilder,
                                    processedTypes,
                                    toConfigure);
                    if (!switchUsed) {
                        method.addContent("}");
                    } else {
                        method.addContentLine("break;");
                        method.decreaseContentPadding();
                    }
                }
            }
            if (switchUsed) {
                method.addContentLine("default:").padContent();
            } else {
                method.addContentLine(" else {");
            }
            if (converterInfo.failOnUnknown()) {
                method.addContent("throw parser.createException(\"Unknown properties are not allowed for this type: \" + ")
                        .addContent(converterInfo.converterType()).addContentLine(".class.getName());");
            } else {
                method.addContentLine("parser.skip();");
            }
            method.addContentLine("}");
        } else if (converterInfo.failOnUnknown()) {
            method.addContent("throw parser.createException(\"Unknown properties are not allowed for this type: \" + ")
                    .addContent(converterInfo.converterType()).addContentLine(".class.getName());");
        } else {
            method.addContentLine("parser.skip();");
        }
        method.addContentLine("lastByte = parser.nextToken();")
                .addContent("if (lastByte == ")
                .addContent(BYTES)
                .addContentLine(".COMMA_BYTE) {")
                .addContentLine("lastByte = parser.nextToken();")
                .addContentLine("continue;")
                .decreaseContentPadding()
                .addContent("} else if (lastByte == ")
                .addContent(BYTES)
                .addContentLine(".BRACE_CLOSE_BYTE) {")
                .addContentLine("break;")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .addContentLine("throw parser.createException(\"Expected ',' or '}'\", lastByte);")
                .addContentLine("}");
        method.addContentLine("}");
    }

    private static void earlyReturnForEmptyObjects(Method.Builder method,
                                                   ConvertedTypeInfo converterInfo,
                                                   List<JsonProperty> jsonProperties,
                                                   boolean hasCreator,
                                                   ElementKind creatorKind,
                                                   CreatorInfo creatorInfo,
                                                   boolean hasBuilder) {
        method.addContent("if (lastByte == ")
                .addContent(BYTES)
                .addContentLine(".BRACE_CLOSE_BYTE) {");
        String required = jsonProperties.stream()
                .filter(JsonProperty::required)
                .map(it -> it.deserializationName().orElseThrow())
                .collect(Collectors.joining(", "));
        if (!required.isEmpty()) {
            method.addContentLine("throw parser.createException(\"The following properties were required to be present, "
                                          + "but none were found: " + required + "\");");
        } else if (hasCreator) {
            TypeName originalType = converterInfo.objectsGenerics();

            method.addContent("return ");
            if (creatorKind == ElementKind.METHOD) {
                method.addContent(originalType).addContent("." + creatorInfo.method() + "(");
            } else {
                method.addContent("new ").addContent(originalType).addContent("(");
            }
            String properties = jsonProperties.stream()
                    .filter(JsonProperty::usedInCreator)
                    .map(property -> property.deserializationName().orElseThrow() + PROPERTY_NAME_SUFFIX)
                    .collect(Collectors.joining(", "));
            method.addContent(properties).addContentLine(");");
        } else if (hasBuilder) {
            BuilderInfo builderInfo = converterInfo.builderInfo().get();
            method.addContent("return builder.").addContent(builderInfo.buildMethodName()).addContentLine("();");
        } else {
            method.addContentLine("return instance;");
        }
        method.addContentLine("}");
    }

    private static void createPreProcessingVariables(Method.Builder method,
                                                     ConvertedTypeInfo converterInfo,
                                                     List<JsonProperty> jsonProperties,
                                                     boolean hasCreator,
                                                     ElementKind creatorKind,
                                                     CreatorInfo creatorInfo,
                                                     boolean hasBuilder) {
        jsonProperties.stream()
                .filter(JsonProperty::required)
                .map(JsonConverterGenerator::convertToMissingName)
                .forEach(name -> method.addContent(boolean.class).addContentLine(" " + name + " = true;"));
        if (hasCreator) {
            for (JsonProperty jsonProperty : jsonProperties) {
                TypeName type = jsonProperty.deserializationType().orElseThrow();
                method.addContent(type)
                        .addContent(" " + jsonProperty.deserializationName().orElseThrow() + PROPERTY_NAME_SUFFIX + " = ")
                        .addContentLine(DEFAULT_TYPE_VALUES.getOrDefault(type, DEFAULT_TYPE_VALUE).get() + ";");
            }
        } else if (creatorKind == ElementKind.METHOD) {
            TypeName originalType = converterInfo.objectsGenerics();
            method.addContent(originalType).addContent(" instance = ")
                    .addContent(originalType).addContent("." + creatorInfo.method() + "();");
        } else if (hasBuilder) {
            BuilderInfo builderInfo = converterInfo.builderInfo().get();
            TypeName builder = builderInfo.builderType();
            method.addContent(builder).addContent(" builder = ");
            if (builderInfo.builderMethodName().isPresent()) {
                TypeName originalType = converterInfo.originalType().genericTypeName();
                method.addContent(originalType).addContentLine(".builder();");
            } else {
                method.addContent("new ").addContent(builder).addContentLine("();");
            }
            for (JsonProperty jsonProperty : jsonProperties) {
                String deserializationName = jsonProperty.deserializationName().orElseThrow();
                if (jsonProperty.usedInBuilder()) {
                    //This property is handled by the builder
                    continue;
                }
                TypeName type = jsonProperty.deserializationType().orElseThrow();
                method.addContent(type)
                        .addContent(" " + deserializationName + PROPERTY_NAME_SUFFIX + " = ")
                        .addContentLine(DEFAULT_TYPE_VALUES.getOrDefault(type, DEFAULT_TYPE_VALUE).get() + ";");
            }
        } else {
            TypeName originalType = converterInfo.objectsGenerics();
            method.addContent(originalType).addContent(" instance = new ")
                    .addContent(originalType).addContentLine("();");
        }
    }

    private static String convertToMissingName(JsonProperty jsonProperty) {
        return jsonProperty.deserializationName()
                .map(it -> it + MISSING_SUFFIX)
                .orElseThrow();
    }

    private static String constantName(String propertyName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_');
            }
            result.append(Character.toUpperCase(c));
        }
        return result.toString();
    }

    private static void addTypeMethodFactory(Method.Builder method, ConvertedTypeInfo converterInfo) {
        method.name("type")
                .returnType(builder -> builder.type(TypeName.builder()
                                                            .from(TypeNames.GENERIC_TYPE)
                                                            .addTypeArgument(converterInfo.wildcardsGenerics())
                                                            .build()))
                .addAnnotation(Annotation.create(Override.class))
                .addContent("return ")
                .addContent(TypeNames.GENERIC_TYPE)
                .addContent(".create(type);");
    }

    private static void addTypeMethod(Method.Builder method, ConvertedTypeInfo converterInfo) {
        method.name("type")
                .returnType(builder -> builder.type(TypeName.builder()
                                                            .from(TypeNames.GENERIC_TYPE)
                                                            .addTypeArgument(converterInfo.wildcardsGenerics())
                                                            .build()))
                .addAnnotation(Annotation.create(Override.class))
                .addContent("return ")
                .addContent(TypeNames.GENERIC_TYPE)
                .addContent(".create(")
                .addContent(converterInfo.originalType())
                .addContentLine(".class);");
    }

    private static void addTypeHandling(JsonProperty jsonProperty,
                                        Method.Builder method,
                                        ClassBase.Builder<?, ?> classBuilder,
                                        boolean hasCreator,
                                        boolean hasBuilder,
                                        Set<String> processedTypes,
                                        Map<String, TypeToConfigure> toConfigure) {
        jsonProperty.deserializer()
                .ifPresentOrElse(deserializer -> addUserDeserializer(jsonProperty,
                                                                     deserializer,
                                                                     method,
                                                                     classBuilder,
                                                                     hasCreator,
                                                                     hasBuilder),
                                 () -> {
                                     TypeName type = jsonProperty.deserializationType().orElseThrow();
                                     createTypeDeserializer(jsonProperty,
                                                            type,
                                                            method,
                                                            classBuilder,
                                                            hasCreator,
                                                            hasBuilder,
                                                            processedTypes,
                                                            toConfigure);
                                 });
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void createTypeDeserializer(JsonProperty jsonProperty,
                                               TypeName type,
                                               Method.Builder method,
                                               ClassBase.Builder<?, ?> classBuilder,
                                               boolean hasCreator,
                                               boolean hasBuilder,
                                               Set<String> processedTypes,
                                               Map<String, TypeToConfigure> toConfigure) {
        TypeName resolvedType = type.boxed();
        if (type.typeArguments().isEmpty()) {
            String converterFieldName = "deserializer" + ensureUpperStart(type);
            if (!processedTypes.contains(converterFieldName)) {
                //Deserializer for this type has not been created yet.
                processedTypes.add(converterFieldName); //To ensure deserializer reusability
                TypeName deserializerArgument = resolvedType.generic() ? TypeNames.OBJECT : resolvedType;
                TypeName fieldType = TypeName.builder(JsonTypes.JSON_DESERIALIZER_TYPE)
                        .addTypeArgument(deserializerArgument)
                        .build();
                classBuilder.addField(builder -> builder.name(converterFieldName)
                        .isVolatile(true)
                        .type(fieldType));
                toConfigure.putIfAbsent(converterFieldName, new TypeToConfigure(TypeConfigMode.DESERIALIZATION,
                                                                                converterFieldName,
                                                                                resolvedType,
                                                                                type,
                                                                                fieldType));
            }
            valueWritingMethod(jsonProperty, method, hasCreator, hasBuilder, converterFieldName);
        } else {
            //Type contains generics
            String fieldName = "deserializer" + ensureUpperStart(jsonProperty.deserializationName().orElseThrow());
            TypeName fieldType = TypeName.builder(JsonTypes.JSON_DESERIALIZER_TYPE).addTypeArgument(resolvedType).build();
            classBuilder.addField(builder -> builder.name(fieldName)
                    .isVolatile(true)
                    .type(fieldType));
            toConfigure.putIfAbsent(fieldName, new TypeToConfigure(TypeConfigMode.DESERIALIZATION,
                                                                   fieldName,
                                                                   resolvedType,
                                                                   type,
                                                                   fieldType));
            valueWritingMethod(jsonProperty, method, hasCreator, hasBuilder, fieldName);
        }
    }

    private static void addUserDeserializer(JsonProperty property,
                                            TypeName deserializer,
                                            Method.Builder method,
                                            ClassBase.Builder<?, ?> classBuilder,
                                            boolean hasCreator,
                                            boolean builderInfo) {
        String constantName = constantName(property.deserializationName().orElseThrow()) + "_DESERIALIZER";
        classBuilder.addField(field -> field.name(constantName)
                .type(deserializer)
                .isStatic(true)
                .isFinal(true)
                .addContent("new ").addContent(deserializer).addContent("()"));

        valueWritingMethod(property, method, hasCreator, builderInfo, constantName);
    }

    private static void valueWritingMethod(JsonProperty property,
                                           Method.Builder method,
                                           boolean hasCreator,
                                           boolean hasBuilder,
                                           String reference) {
        if (hasCreator || (hasBuilder && !property.usedInBuilder())) {
            String deserPropertyName = property.deserializationName().orElseThrow();
            method.addContent(deserPropertyName + PROPERTY_NAME_SUFFIX + " = ")
                    .addContentLine("parser.checkNull() ? " + reference + ".deserializeNull() : "
                                            + reference + ".deserialize(parser);");
        } else {
            String instanceName = hasBuilder ? "builder" : "instance";
            String writingMethod = property.setterName()
                    .map(methodName -> instanceName + "." + methodName
                            + "(parser.checkNull() ? " + reference + ".deserializeNull() : "
                            + reference + ".deserialize(parser));")
                    .orElseGet(() -> property.fieldName()
                            .filter(it -> property.directFieldWrite())
                            .map(fieldName -> instanceName + "." + fieldName
                                    + " = parser.checkNull() ? " + reference + ".deserializeNull() : "
                                    + reference + ".deserialize(parser);")
                            .orElseThrow()); // No valid setter or field; unreachable due to earlier filtering.
            method.addContentLine(writingMethod);
        }
        if (property.required()) {
            String name = convertToMissingName(property);
            method.addContentLine(name + " = false;");
        }
    }

    private static String ensureUpperStart(TypeName typeName) {
        String className = typeName.toString();
        int index = className.lastIndexOf(".");
        if (index > -1) {
            className = className.substring(index + 1);
        }
        return ensureUpperStart(className.replaceAll("\\[]", "Array"));
    }

    private static String ensureUpperStart(String str) {
        if (Character.isUpperCase(str.charAt(0))) {
            return str;
        } else if (str.length() == 1) {
            return str.toUpperCase();
        } else {
            return Character.toUpperCase(str.charAt(0)) + str.substring(1);
        }
    }

    private static int calculateNameHash(String name) {
        int fnvHash = FNV_OFFSET_BASIS;
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            fnvHash ^= (b & 0xFF);
            fnvHash *= FNV_PRIME;
        }
        return fnvHash;
    }

    private enum TypeConfigMode {
        SERIALIZATION("serializer"),
        DESERIALIZATION("deserializer");

        private final String method;

        TypeConfigMode(String method) {
            this.method = method;
        }
    }

    private record TypeToConfigure(TypeConfigMode mode,
                                   String fieldName,
                                   TypeName resolved,
                                   TypeName original,
                                   TypeName fieldType) {
    }

    private static final class MethodNameCounter {

        private int count = 0;

    }

}
