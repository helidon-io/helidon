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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.Executable;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.json.codegen.ConvertedTypeInfo.needsResolving;
import static io.helidon.json.codegen.JsonTypes.BYTES;
import static java.util.function.Predicate.not;

class JsonConverterGenerator {

    static final String CONFIGURE_PARAM = "jsonBindingConfigurator";
    // Bound the amount of additional value-reading code generated for small objects.
    private static final int MAX_ORDERED_PROPERTIES = 12;
    // Wide integer creators otherwise repeat the same value read at every case of a large switch.
    private static final int MIN_INDEXED_INTEGER_PROPERTIES = 64;
    private static final String PROPERTY_NAME_SUFFIX = "_";
    private static final String MISSING_SUFFIX = "_missing";
    private static final Supplier<?> DEFAULT_TYPE_VALUE = () -> null;
    private static final Map<TypeName, Supplier<?>> DEFAULT_TYPE_VALUES;
    private static final String WRITE_NULLS = "writeNulls";

    static {
        Map<TypeName, Supplier<?>> tmp = new HashMap<>();

        tmp.put(TypeNames.PRIMITIVE_BOOLEAN, () -> false);
        tmp.put(TypeNames.PRIMITIVE_BYTE, () -> 0);
        tmp.put(TypeNames.PRIMITIVE_SHORT, () -> 0);
        tmp.put(TypeNames.PRIMITIVE_INT, () -> 0);
        tmp.put(TypeNames.PRIMITIVE_LONG, () -> 0);
        tmp.put(TypeNames.PRIMITIVE_FLOAT, () -> "0.0F");
        tmp.put(TypeNames.PRIMITIVE_DOUBLE, () -> "0.0");
        tmp.put(TypeNames.OPTIONAL, () -> "@java.util.Optional@.empty()");
        tmp.put(TypeNames.OPTIONAL_INT, () -> "@java.util.OptionalInt@.empty()");
        tmp.put(TypeNames.OPTIONAL_DOUBLE, () -> "@java.util.OptionalDouble@.empty()");
        tmp.put(TypeNames.OPTIONAL_LONG, () -> "@java.util.OptionalLong@.empty()");

        DEFAULT_TYPE_VALUES = Map.copyOf(tmp);
    }

    private JsonConverterGenerator() {
    }

    static void generateConverter(ClassBase.Builder<?, ?> classBuilder, ConvertedTypeInfo converterInfo, boolean factory) {
        Set<String> reservedNames = new HashSet<>(Set.of("PROPERTY_NAMES", "POLYMORPHIC_PROPERTY", "POLYMORPHIC_ALIASES",
                                                      "PROPERTY_NAMES_TRANSITIONS", "POLYMORPHIC_PROPERTY_TRANSITIONS",
                                                      "POLYMORPHIC_ALIASES_TRANSITIONS"));
        reservedNames.add(converterInfo.originalType().className());
        reservedNames.addAll(converterInfo.originalType().enclosingNames());
        for (JsonProperty property : converterInfo.jsonProperties().values()) {
            reservedNames.add(property.identifier() + PROPERTY_NAME_SUFFIX);
            if (property.required()) {
                reservedNames.add(convertToMissingName(property));
            }
        }
        GeneratedNames fieldNames = new GeneratedNames(reservedNames);
        TypeName converterInterfaceType = TypeName.builder()
                .from(JsonTypes.JSON_CONVERTER_TYPE)
                .addTypeArgument(converterInfo.wildcardsGenerics())
                .build();

        Map<String, TypeToConfigure> toConfigure = new HashMap<>();
        classBuilder.name(converterInfo.converterType().className())
                .addInterface(converterInterfaceType)
                .javadoc(Javadoc.builder()
                                 .add("Json converter for {@link " + converterInfo.originalType().fqName() + "}.")
                                 .build());

        boolean hasPolymorphicInfo = converterInfo.polymorphicInfo().isPresent();
        boolean specialPolyConverter = converterInfo.polymorphicInfo().map(PolymorphicInfo::classConverterMethods).orElse(false);
        if (hasPolymorphicInfo) {
            classBuilder.addField(field -> field.name("configurator").type(JsonTypes.JSON_BINDING_CONFIGURATOR).isVolatile(true))
                    .addMethod(method -> generatePolySerializeMethod(classBuilder,
                                                                     method,
                                                                     converterInfo,
                                                                     converterInfo.polymorphicInfo().get(),
                                                                     toConfigure,
                                                                     fieldNames))
                    .addMethod(method -> generatePolyDeserializeMethod(classBuilder,
                                                                       method,
                                                                       converterInfo,
                                                                       converterInfo.polymorphicInfo().get(),
                                                                       toConfigure,
                                                                       fieldNames));
        }
        if (!hasPolymorphicInfo || specialPolyConverter) {
            classBuilder.addMethod(method -> generateSerializeMethod(classBuilder,
                                                                     method,
                                                                     converterInfo,
                                                                     toConfigure,
                                                                     specialPolyConverter,
                                                                     fieldNames))
                    .addMethod(method -> generateDeserializeMethod(classBuilder,
                                                                   method,
                                                                   converterInfo,
                                                                   toConfigure,
                                                                   specialPolyConverter,
                                                                   fieldNames));
        }

        if (factory) {
            classBuilder.addMethod(method -> addConfigurationFactory(method, toConfigure, converterInfo, hasPolymorphicInfo));
            classBuilder.addMethod(method -> addTypeMethodFactory(method, converterInfo));
        } else {
            classBuilder.addMethod(method -> addConfigurationMethod(method, toConfigure, hasPolymorphicInfo));
            classBuilder.addMethod(method -> addTypeMethod(method, converterInfo));
        }
    }

    private static void generatePolySerializeMethod(ClassBase.Builder<?, ?> classBuilder,
                                                    Method.Builder method,
                                                    ConvertedTypeInfo converterInfo,
                                                    PolymorphicInfo polymorphicInfo,
                                                    Map<String, TypeToConfigure> toConfigure,
                                                    GeneratedNames fieldNames) {
        method.name("serialize")
                .addParameter(JsonTypes.JSON_GENERATOR, "generator")
                .addParameter(converterInfo.wildcardsGenerics(), "instance")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, WRITE_NULLS)
                .addAnnotation(Annotation.create(Override.class));

        Set<String> createdSerializers = new HashSet<>();
        boolean first = true;
        for (TypeName typeName : polymorphicInfo.serializationTypes()) {
            TypeName resolved = typeName.boxed();
            String fieldName = fieldNames.serializer(typeName, "serializer" + ensureUpperStart(typeName));
            if (!createdSerializers.contains(fieldName)) {
                createdSerializers.add(fieldName);
                TypeName converterType = TypeName.builder()
                        .from(JsonTypes.JSON_SERIALIZER_TYPE)
                        .addTypeArgument(resolved)
                        .build();
                classBuilder.addField(fieldBuilder -> fieldBuilder.name(fieldName)
                        .isVolatile(true)
                        .type(converterType));
                toConfigure.putIfAbsent(fieldName,
                                        new TypeToConfigure(TypeConfigMode.SERIALIZATION,
                                                            fieldName,
                                                            resolved,
                                                            typeName,
                                                            converterType));
            }
            if (first) {
                first = false;
                method.addContent("if (instance instanceof ");
            } else {
                method.addContent("}") //Automatic padding decrease
                        .addContent(" else if (instance instanceof ");
            }
            method.addContent(resolved)
                    .addContentLine(" _instance) {")
                    .addContentLine(fieldName + ".serialize(generator, _instance, writeNulls);");
        }
        if (polymorphicInfo.classConverterMethods()) {
            if (!first) {
                method.addContent("}") //Automatic padding decrease
                        .addContent(" else ");
            }
            first = false;
            method.addContent("if (instance.getClass() == ")
                    .addContent(converterInfo.originalType())
                    .addContentLine(".class) {")
                    .addContentLine("serializeSelf(generator, instance);");
        }
        TypeName serializerType = TypeName.builder()
                .from(JsonTypes.JSON_SERIALIZER_TYPE)
                .addTypeArgument(converterInfo.originalType())
                .build();
        if (!first) {
            method.addContent("}") //Automatic padding decrease
                    .addContentLine(" else {");
        }
        method.addContent(serializerType)
                .addContent(" defaultSerializer = (")
                .addContent(serializerType)
                .addContentLine(") configurator.serializer(instance.getClass());");
        method.addContentLine("if (defaultSerializer == this) {");
        method.addContent("throw new ")
                .addContent(JsonTypes.JSON_BINDING_EXCEPTION)
                .addContentLine("(\"Obtained the same serializer. "
                                        + "This would cause an infinite cycle. Type: \" + instance.getClass());");
        method.addContentLine("}");
        method.addContentLine("defaultSerializer.serialize(generator, instance, writeNulls);");
        if (!first) {
            method.addContent("}");
        }
    }

    private static void generatePolyDeserializeMethod(ClassBase.Builder<?, ?> classBuilder,
                                                      Method.Builder method,
                                                      ConvertedTypeInfo converterInfo,
                                                      PolymorphicInfo polymorphicInfo,
                                                      Map<String, TypeToConfigure> toConfigure,
                                                      GeneratedNames fieldNames) {
        Set<String> createdDeserializers = new HashSet<>();
        boolean hasDefault = polymorphicInfo.defaultSubtype().isPresent();

        method.name("deserialize")
                .returnType(converterInfo.wildcardsGenerics())
                .addParameter(JsonTypes.JSON_PARSER, "parser")
                .addAnnotation(Annotation.create(Override.class))
                .addContent(byte.class).addContentLine(" lastByte = parser.currentByte();")
                .addContent("if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".BRACE_OPEN_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected '{' to start an object\", lastByte);")
                .addContentLine("}")
                .addContentLine("parser.mark();")
                .addContentLine("lastByte = parser.nextToken();")
                .addContent("if (lastByte == ")
                .addContent(BYTES)
                .addContentLine(".BRACE_CLOSE_BYTE) {");

        if (hasDefault) {
            addPolyDeserializerInvocation(classBuilder,
                                          method,
                                          toConfigure,
                                          polymorphicInfo.defaultSubtype().get(),
                                          createdDeserializers,
                                          true,
                                          fieldNames);
        } else if (polymorphicInfo.classConverterMethods()) {
            method.addContentLine("parser.resetToMark();")
                    .addContentLine("return deserializeSelf(parser);");
        } else {
            method.addContentLine("throw parser.createException(\"Missing property containing alias\", lastByte);");
        }
        method.addContent("}")
                .addContent(" else if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".DOUBLE_QUOTE_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected '\\\"' as a key start\", lastByte);")
                .addContentLine("}");

        StringLexerGenerator.generate(classBuilder,
                                      converterInfo.converterType(),
                                      "POLYMORPHIC_PROPERTY",
                                      List.of(polymorphicInfo.property()));
        method.addContentLine("if (parser.readString(POLYMORPHIC_PROPERTY) != 0) {")
                .addContentLine("return deserializeUnordered(parser);")
                .addContentLine("}");
        method.addContentLine("lastByte = parser.nextToken();")
                .addContent("if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".COLON_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected ':' to separate key and value\", lastByte);")
                .addContentLine("}")
                .addContentLine("lastByte = parser.nextToken();")
                .addContent("if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".DOUBLE_QUOTE_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected '\\\"' as an alias value\", lastByte);")
                .addContentLine("}")
                .addContentLine("parser.clearMark();");
        List<Map.Entry<String, TypeName>> aliases = new ArrayList<>(polymorphicInfo.aliases().entrySet());
        StringLexerGenerator.generate(classBuilder,
                                      converterInfo.converterType(),
                                      "POLYMORPHIC_ALIASES",
                                      aliases.stream().map(Map.Entry::getKey).toList());
        method.addContentLine("int alias = parser.readString(POLYMORPHIC_ALIASES);")
                .addContentLine("lastByte = parser.nextToken();")
                .addContent("if (lastByte == ")
                .addContent(BYTES)
                .addContentLine(".COMMA_BYTE) {")
                .addContentLine("parser.nextToken();")
                .addContent("}")
                .addContent(" else if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".BRACE_CLOSE_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected ',' or '}' as the next bytes\", lastByte);")
                .addContentLine("}");

        List<String> aliasConstants = processAliasValues(classBuilder,
                                                         method,
                                                         toConfigure,
                                                         aliases,
                                                         createdDeserializers,
                                                         fieldNames);
        classBuilder.addMethod(unordered -> generateUnorderedPolyDeserializeMethod(unordered,
                                                                                   converterInfo,
                                                                                   polymorphicInfo,
                                                                                   aliases,
                                                                                   aliasConstants,
                                                                                   fieldNames));
    }

    private static void generateUnorderedPolyDeserializeMethod(Method.Builder method,
                                                               ConvertedTypeInfo converterInfo,
                                                               PolymorphicInfo polymorphicInfo,
                                                               List<Map.Entry<String, TypeName>> aliases,
                                                               List<String> aliasConstants,
                                                               GeneratedNames fieldNames) {
        method.name("deserializeUnordered")
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(converterInfo.wildcardsGenerics())
                .addParameter(JsonTypes.JSON_PARSER, "parser")
                .addContentLine("int propertyIndex = 0;")
                .addContentLine("while (true) {")
                .addContentLine("if (parser.nextToken() != ':') {")
                .addContentLine("throw parser.createException(\"Expected ':' to separate key and value\");")
                .addContentLine("}")
                .addContentLine("parser.nextToken();")
                .addContentLine("parser.skip();")
                .addContentLine("byte token = parser.nextToken();")
                .addContentLine("if (token == '}') {");
        if (polymorphicInfo.defaultSubtype().isPresent()) {
            TypeName type = polymorphicInfo.defaultSubtype().get();
            String fieldName = fieldNames.deserializer(type, "deserializer" + ensureUpperStart(type));
            method.addContentLine("parser.resetToMark();")
                    .addContentLine("return " + fieldName + ".deserialize(parser);");
        } else if (polymorphicInfo.classConverterMethods()) {
            method.addContentLine("parser.resetToMark();")
                    .addContentLine("return deserializeSelf(parser);");
        } else {
            method.addContentLine("throw parser.createException(\"Missing property containing alias\");");
        }
        method.addContentLine("}")
                .addContentLine("if (token != ',') {")
                .addContentLine("throw parser.createException(\"Expected ',' or '}' after property value\", token);")
                .addContentLine("}")
                .addContentLine("if (parser.nextToken() != '\"') {")
                .addContentLine("throw parser.createException(\"Expected a property name\");")
                .addContentLine("}")
                .addContentLine("propertyIndex++;")
                .addContentLine("if (parser.readString(POLYMORPHIC_PROPERTY) == 0) {")
                .addContentLine("break;")
                .addContentLine("}")
                .addContentLine("}")
                .addContentLine("if (parser.nextToken() != ':') {")
                .addContentLine("throw parser.createException(\"Expected ':' to separate key and value\");")
                .addContentLine("}")
                .addContentLine("if (parser.nextToken() != '\"') {")
                .addContentLine("throw parser.createException(\"Expected a string alias value\");")
                .addContentLine("}")
                .addContentLine("int alias = parser.readString(POLYMORPHIC_ALIASES);")
                .addContentLine("byte token = parser.nextToken();")
                .addContentLine("if (token != ',' && token != '}') {")
                .addContentLine("throw parser.createException(\"Expected ',' or '}' after alias value\", token);")
                .addContentLine("}");
        if (aliases.isEmpty()) {
            method.addContentLine("throw parser.createException(\"Unknown alias value\");");
            return;
        }
        method.addContentLine("return switch (alias) {");
        for (int i = 0; i < aliases.size(); i++) {
            String alias = aliases.get(i).getKey();
            TypeName type = aliases.get(i).getValue();
            String fieldName = fieldNames.deserializer(type, "deserializer" + ensureUpperStart(type));
            addOrdinalCase(method, alias, !GeneratedNames.normalize(alias).equals(alias), aliasConstants.get(i));
            method.addContentLine("parser.resetToMark();")
                    .addContent("yield " + fieldName + ".deserialize(")
                    .addContent(JsonTypes.OBJECT_START_PARSER)
                    .addContentLine(".create(parser, propertyIndex));")
                    .addContentLine("}");
        }
        method.addContentLine("default -> throw parser.createException(\"Unknown alias value\");")
                .addContent("}")
                .addContentLine(";");
    }

    private static List<String> processAliasValues(ClassBase.Builder<?, ?> classBuilder,
                                                   Method.Builder method,
                                                   Map<String, TypeToConfigure> toConfigure,
                                                   List<Map.Entry<String, TypeName>> aliases,
                                                   Set<String> createdDeserializers,
                                                   GeneratedNames fieldNames) {
        List<String> aliasConstants = new ArrayList<>();
        method.addContentLine("switch (alias) {");
        for (int i = 0; i < aliases.size(); i++) {
            String alias = aliases.get(i).getKey();
            aliasConstants.add(addOrdinalCase(classBuilder,
                                             method,
                                             fieldNames,
                                             alias,
                                             !GeneratedNames.normalize(alias).equals(alias),
                                             i));
            addPolyDeserializerInvocation(classBuilder,
                                          method,
                                          toConfigure,
                                          aliases.get(i).getValue(),
                                          createdDeserializers,
                                          false,
                                          fieldNames);
            method.addContentLine("}");
        }
        method.addContentLine("default -> throw parser.createException(\"Unknown alias value\");");
        method.addContentLine("}");
        return aliasConstants;
    }

    private static void addPolyDeserializerInvocation(ClassBase.Builder<?, ?> classBuilder,
                                                      Method.Builder method,
                                                      Map<String, TypeToConfigure> toConfigure,
                                                      TypeName type,
                                                      Set<String> createdDeserializers,
                                                      boolean resetToMark,
                                                      GeneratedNames fieldNames) {
        String fieldName = fieldNames.deserializer(type, "deserializer" + ensureUpperStart(type));
        if (!createdDeserializers.contains(fieldName)) {
            createdDeserializers.add(fieldName);
            TypeName converterType = TypeName.builder()
                    .from(JsonTypes.JSON_DESERIALIZER_TYPE)
                    .addTypeArgument(type)
                    .build();
            classBuilder.addField(fieldBuilder -> fieldBuilder.name(fieldName)
                    .isVolatile(true)
                    .type(converterType));
            toConfigure.putIfAbsent(fieldName,
                                    new TypeToConfigure(TypeConfigMode.DESERIALIZATION,
                                                        fieldName,
                                                        type,
                                                        type,
                                                        converterType));
        }
        if (resetToMark) {
            method.addContentLine("parser.resetToMark();");
            method.addContentLine("return " + fieldName + ".deserialize(parser);");
        } else {
            method.addContent("return " + fieldName + ".deserialize(")
                    .addContent(JsonTypes.OBJECT_START_PARSER)
                    .addContentLine(".create(parser));");
        }
    }

    private static void addConfigurationMethod(Method.Builder method,
                                               Map<String, TypeToConfigure> toConfigure,
                                               boolean saveConfigurator) {
        method.name("configure")
                .addAnnotation(Annotation.create(Override.class))
                .addParameter(JsonTypes.JSON_BINDING_CONFIGURATOR, CONFIGURE_PARAM);

        if (saveConfigurator) {
            method.addContentLine("this.configurator = " + CONFIGURE_PARAM + ";");
        }

        initializeNoRuntimeResolving(method, toConfigure);
    }

    private static void addConfigurationFactory(Method.Builder method,
                                                Map<String, TypeToConfigure> toConfigure,
                                                ConvertedTypeInfo convertedTypeInfo,
                                                boolean saveConfigurator) {
        method.name("configure")
                .addAnnotation(Annotation.create(Override.class))
                .addParameter(JsonTypes.JSON_BINDING_CONFIGURATOR, CONFIGURE_PARAM);

        if (saveConfigurator) {
            method.addContentLine("this.configurator = " + CONFIGURE_PARAM + ";");
        }

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

    private static void generateSerializeMethod(ClassBase.Builder<?, ?> classBuilder,
                                                Method.Builder method,
                                                ConvertedTypeInfo converterInfo,
                                                Map<String, TypeToConfigure> toConfigure,
                                                boolean specialPolyConverter,
                                                GeneratedNames fieldNames) {
        if (specialPolyConverter) {
            method.name("serializeSelf")
                    .addParameter(JsonTypes.JSON_GENERATOR, "generator")
                    .addParameter(converterInfo.wildcardsGenerics(), "instance")
                    .accessModifier(AccessModifier.PRIVATE);
        } else {
            method.name("serialize")
                    .addParameter(JsonTypes.JSON_GENERATOR, "generator")
                    .addParameter(converterInfo.wildcardsGenerics(), "instance")
                    .addParameter(TypeNames.PRIMITIVE_BOOLEAN, WRITE_NULLS)
                    .addAnnotation(Annotation.create(Override.class));
        }
        method.addContentLine("generator.writeObjectStart();");

        addExtraProperties(method, converterInfo);

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
            String key = jsonProperty.serializationName().orElseThrow();
            String keyFieldName = fieldNames.unique(constantName(key) + "_KEY");
            fieldNames.registerJsonKey(key, keyFieldName);
            classBuilder.addField(field -> field.name(keyFieldName)
                    .type(JsonTypes.JSON_KEY)
                    .isStatic(true)
                    .isFinal(true)
                    .addContent(JsonTypes.JSON_KEY)
                    .addContent(".create(")
                    .addContentLiteral(key)
                    .addContent(")"));

            String fieldName = jsonProperty.serializer()
                    .map(serializer -> {
                        String constantName = fieldNames.unique(constantName(key) + "_SERIALIZER");
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
                            fn = fieldNames.unique("serializer" + capitalize(key));
                        } else {
                            fn = fieldNames.serializer(type, "serializer" + ensureUpperStart(type));
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

            method.addContent(JsonTypes.JSON_SERIALIZERS)
                    .addContentLine(".serialize(generator, " + fieldName + ", "
                                            + "instance." + accessor + ", "
                                            + keyFieldName + ", "
                                            + jsonProperty.nullable() + ");");
        }
        method.addContentLine("generator.writeObjectEnd();");
    }

    private static void addExtraProperties(Method.Builder method, ConvertedTypeInfo converterInfo) {
        for (Map.Entry<String, String> entry : converterInfo.extraProperties().entrySet()) {
            method.addContentLine("generator.write(\"" + entry.getKey() + "\", \"" + entry.getValue() + "\");");
        }
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
    private static void generateDeserializeMethod(ClassBase.Builder<?, ?> classBuilder,
                                                  Method.Builder method,
                                                  ConvertedTypeInfo converterInfo,
                                                  Map<String, TypeToConfigure> toConfigure,
                                                  boolean specialPolyConverter,
                                                  GeneratedNames fieldNames) {
        CreatorInfo creatorInfo = converterInfo.creatorInfo();
        ElementKind creatorKind = creatorInfo.creatorKind();
        boolean hasCreator = creatorKind != null && !creatorInfo.parameters().isEmpty();
        boolean hasBuilder = converterInfo.builderInfo().isPresent();
        List<JsonProperty> jsonProperties = converterInfo.jsonProperties()
                .values()
                .stream()
                .filter(JsonConverterGenerator::usableForDeserialization)
                .toList();

        if (specialPolyConverter) {
            method.name("deserializeSelf")
                    .returnType(converterInfo.wildcardsGenerics())
                    .addParameter(JsonTypes.JSON_PARSER, "parser")
                    .accessModifier(AccessModifier.PRIVATE);
        } else {
            method.name("deserialize")
                    .returnType(converterInfo.wildcardsGenerics())
                    .addParameter(JsonTypes.JSON_PARSER, "parser")
                    .addAnnotation(Annotation.create(Override.class));
        }
        method.addContent(byte.class).addContentLine(" lastByte = parser.currentByte();")
                .addContent("if (lastByte != ")
                .addContent(BYTES)
                .addContentLine(".BRACE_OPEN_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected '{' to start an object\", lastByte);")
                .addContentLine("}")
                .addContentLine("lastByte = parser.nextToken();");
        createPreProcessingVariables(method, converterInfo, jsonProperties, hasCreator, creatorKind, creatorInfo, hasBuilder);
        earlyReturnForEmptyObjects(method, converterInfo, jsonProperties, hasCreator, creatorKind, creatorInfo, hasBuilder);
        propertyProcessing(classBuilder, method, converterInfo, toConfigure, jsonProperties, hasCreator, hasBuilder, fieldNames);
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
            method.addContent(creatorArguments(converterInfo, creatorInfo)).addContentLine(");");
            for (JsonProperty property : jsonProperties) {
                if (!property.usedInCreator()) {
                    if (property.directFieldWrite()) {
                        method.addContentLine("instance." + property.fieldName().orElseThrow() + " = "
                                                      + property.identifier() + PROPERTY_NAME_SUFFIX + ";");
                    } else {
                        method.addContentLine("instance." + property.setterName().orElseThrow() + "("
                                                      + property.identifier() + PROPERTY_NAME_SUFFIX + ");");
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
                                                  + property.identifier() + PROPERTY_NAME_SUFFIX + ";");
                } else {
                    method.addContentLine("instance." + property.setterName().orElseThrow() + "("
                                                  + property.identifier() + PROPERTY_NAME_SUFFIX + ");");
                }
            }
        }
        method.addContentLine("return instance;");
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void propertyProcessing(ClassBase.Builder<?, ?> classBuilder,
                                           Method.Builder method,
                                           ConvertedTypeInfo converterInfo,
                                           Map<String, TypeToConfigure> toConfigure,
                                           List<JsonProperty> jsonProperties,
                                           boolean hasCreator,
                                           boolean hasBuilder,
                                           GeneratedNames fieldNames) {
        List<String> propertyNames = new ArrayList<>(jsonProperties.stream()
                .map(property -> property.deserializationName().orElseThrow())
                .toList());
        List<String> discriminatorNames = converterInfo.extraProperties().keySet()
                .stream()
                .filter(name -> !propertyNames.contains(name))
                .toList();
        propertyNames.addAll(discriminatorNames);
        if (propertyNames.isEmpty()) {
            readUnknownProperties(method, converterInfo);
            return;
        }
        StringLexerGenerator.generate(classBuilder, converterInfo.converterType(), "PROPERTY_NAMES", propertyNames);
        List<PropertyReader> readers = new ArrayList<>(jsonProperties.size());
        Set<String> processedTypes = new HashSet<>();
        for (int i = 0; i < jsonProperties.size(); i++) {
            JsonProperty property = jsonProperties.get(i);
            String constant = addOrdinalConstant(classBuilder, fieldNames, propertyNames.get(i), i);
            String deserializer = deserializerField(property, classBuilder, processedTypes, toConfigure, fieldNames);
            readers.add(new PropertyReader(property, constant, deserializer));
        }
        Map<String, String> ignored = new LinkedHashMap<>();
        for (int i = 0; i < discriminatorNames.size(); i++) {
            String name = discriminatorNames.get(i);
            ignored.put(name, addOrdinalConstant(classBuilder, fieldNames, name, readers.size() + i));
        }
        boolean ordered = readers.size() >= 2 && readers.size() <= MAX_ORDERED_PROPERTIES && discriminatorNames.isEmpty()
                && propertyNames.stream().allMatch(name -> fieldNames.jsonKey(name) != null);
        if (ordered) {
            classBuilder.addStaticImport("io.helidon.json.Parsers.tryReadProperty");
            classBuilder.addStaticImport("io.helidon.json.Parsers.tryNextProperty");
            method.addContentLine("if (tryReadProperty(parser, " + fieldNames.jsonKey(propertyNames.getFirst()) + ")) {");
            String target = "";
            if (!hasCreator) {
                boolean takesBuilder = hasBuilder && converterInfo.creatorInfo().creatorKind() != ElementKind.METHOD;
                target = takesBuilder ? ", builder" : ", instance";
            }
            method.addContentLine("return readDeclaredFields(parser" + target + ");").addContentLine("}");
            generateDeclaredReader(classBuilder, converterInfo, jsonProperties, readers, hasCreator, hasBuilder, fieldNames);
        }
        propertyLoop(method, converterInfo, readers, ignored, hasCreator, hasBuilder, "parser.readProperty(PROPERTY_NAMES)");
    }

    private static void readUnknownProperties(Method.Builder method, ConvertedTypeInfo converterInfo) {
        method.addContentLine("while (true) {")
                .addContent("if (lastByte != ").addContent(BYTES).addContentLine(".DOUBLE_QUOTE_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected '\\\"' as a key start\", lastByte);")
                .addContentLine("}")
                .addContentLine("parser.skip();")
                .addContentLine("lastByte = parser.nextToken();")
                .addContent("if (lastByte != ").addContent(BYTES).addContentLine(".COLON_BYTE) {")
                .addContentLine("throw parser.createException(\"Expected ':' to separate key and value\", lastByte);")
                .addContentLine("}")
                .addContentLine("parser.nextToken();");

        unknownProperty(method, converterInfo);
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

    private static void generateDeclaredReader(ClassBase.Builder<?, ?> classBuilder,
                                               ConvertedTypeInfo converterInfo,
                                               List<JsonProperty> properties,
                                               List<PropertyReader> readers,
                                               boolean hasCreator,
                                               boolean hasBuilder,
                                               GeneratedNames fieldNames) {
        CreatorInfo creator = converterInfo.creatorInfo();
        classBuilder.addMethod(method -> {
            method.name("readDeclaredFields").accessModifier(AccessModifier.PRIVATE)
                    .returnType(converterInfo.wildcardsGenerics()).addParameter(JsonTypes.JSON_PARSER, "parser");
            if (hasCreator) {
                createPreProcessingVariables(method, converterInfo, properties, true, creator.creatorKind(), creator, hasBuilder);
            } else {
                boolean takesBuilder = hasBuilder && creator.creatorKind() != ElementKind.METHOD;
                if (takesBuilder) {
                    method.addParameter(converterInfo.builderInfo().orElseThrow().builderType(), "builder");
                } else {
                    method.addParameter(converterInfo.objectsGenerics(), "instance");
                }
                requiredFlags(method, properties);
                if (takesBuilder) {
                    properties.stream().filter(not(JsonProperty::usedInBuilder)).forEach(p -> addPropertyVariable(method, p));
                }
            }
            PropertyReader first = readers.getFirst();
            valueWritingMethod(first.property(), method, hasCreator, hasBuilder, first.deserializer());
            readConsecutiveProperties(method, readers, hasCreator, hasBuilder, fieldNames);
            propertyLoop(method, converterInfo, readers, Map.of(), hasCreator, hasBuilder,
                         "parser.nextProperty(PROPERTY_NAMES)");
            createFinalInstanceCreation(method, converterInfo, properties, hasCreator, creator.creatorKind(),
                                        creator, hasBuilder);
        });
    }

    private static void propertyLoop(Method.Builder method,
                                     ConvertedTypeInfo converterInfo,
                                     List<PropertyReader> readers,
                                     Map<String, String> ignored,
                                     boolean hasCreator,
                                     boolean hasBuilder,
                                     String firstProperty) {
        if (hasCreator && ignored.isEmpty() && readers.size() >= MIN_INDEXED_INTEGER_PROPERTIES
                && tryGenerateIndexedIntegerLoop(method, converterInfo, readers, firstProperty)) {
            return;
        }
        method.addContent("for (int property = " + firstProperty + "; property != ").addContent(JsonTypes.JSON_PARSER)
                .addContentLine(".END_OBJECT; property = parser.nextProperty(PROPERTY_NAMES)) {")
                .addContentLine("switch (property) {");
        for (PropertyReader reader : readers) {
            JsonProperty property = reader.property();
            String name = property.deserializationName().orElseThrow();
            addOrdinalCase(method, name, !property.identifier().equals(name), reader.constantName());
            valueWritingMethod(property, method, hasCreator, hasBuilder, reader.deserializer());
            method.addContentLine("}");
        }
        ignored.forEach((name, constant) -> {
            addOrdinalCase(method, name, !GeneratedNames.normalize(name).equals(name), constant);
            method.addContentLine("parser.skip();").addContentLine("}");
        });
        method.addContentLine("default -> {");
        unknownProperty(method, converterInfo);
        method.addContentLine("}").addContentLine("}").addContentLine("}");
    }

    private static boolean tryGenerateIndexedIntegerLoop(Method.Builder method,
                                                         ConvertedTypeInfo converterInfo,
                                                         List<PropertyReader> readers,
                                                         String firstProperty) {
        String deserializer = readers.getFirst().deserializer();
        boolean eligible = readers.stream().allMatch(reader -> reader.property().usedInCreator() && !reader.property().required()
                && reader.property().deserializationType().filter(TypeNames.PRIMITIVE_INT::equals).isPresent()
                && deserializer.equals(reader.deserializer()));
        if (!eligible) {
            return false;
        }

        // Keep the constructor arguments out of the hot loop. The surrounding block also scopes the temporary array.
        method.addContentLine("{")
                .addContentLine("int[] fieldValues = null;")
                .addContent("for (int property = " + firstProperty + "; property != ").addContent(JsonTypes.JSON_PARSER)
                .addContentLine(".END_OBJECT; property = parser.nextProperty(PROPERTY_NAMES)) {")
                .addContentLine("if (property >= 0 && property < " + readers.size() + ") {")
                .addContentLine("if (fieldValues == null) {")
                .addContentLine("fieldValues = new int[" + readers.size() + "];")
                .addContentLine("}")
                .addContentLine("fieldValues[property] = parser.checkNull() ? " + deserializer + ".deserializeNull() : "
                                        + deserializer + ".deserialize(parser);")
                .decreaseContentPadding().addContentLine("} else {");
        unknownProperty(method, converterInfo);
        method.addContentLine("}").addContentLine("}");
        method.addContentLine("if (fieldValues != null) {");
        for (int i = 0; i < readers.size(); i++) {
            String variable = readers.get(i).property().identifier() + PROPERTY_NAME_SUFFIX;
            method.addContentLine(variable + " = fieldValues[" + i + "];");
        }
        method.addContentLine("}");
        method.addContentLine("}");
        return true;
    }

    private static void unknownProperty(Method.Builder method, ConvertedTypeInfo converterInfo) {
        if (converterInfo.failOnUnknown()) {
            method.addContent("throw parser.createException(\"Unknown properties are not allowed for this type: \" + ")
                    .addContent(converterInfo.converterType()).addContentLine(".class.getName());");
        } else {
            method.addContentLine("parser.skip();");
        }
    }

    private static void readConsecutiveProperties(Method.Builder method,
                                                  List<PropertyReader> readers,
                                                  boolean hasCreator,
                                                  boolean hasBuilder,
                                                  GeneratedNames fieldNames) {
        method.addContentLine("orderedProperties: {");
        for (int j = 1; j < readers.size(); j++) {
            PropertyReader reader = readers.get(j);
            JsonProperty next = reader.property();
            String key = fieldNames.jsonKey(next.deserializationName().orElseThrow());
            method.addContentLine("if (!tryNextProperty(parser, " + key + ")) {")
                    .addContentLine("break orderedProperties;")
                    .addContentLine("}");
            valueWritingMethod(next, method, hasCreator, hasBuilder, reader.deserializer());
        }
        method.addContentLine("}");
    }

    private static String addOrdinalCase(ClassBase.Builder<?, ?> classBuilder,
                                        Method.Builder method,
                                        GeneratedNames fieldNames,
                                        String value,
                                        boolean normalized,
                                        int index) {
        String constantName = addOrdinalConstant(classBuilder, fieldNames, value, index);
        addOrdinalCase(method, value, normalized, constantName);
        return constantName;
    }

    private static String addOrdinalConstant(ClassBase.Builder<?, ?> classBuilder,
                                            GeneratedNames fieldNames,
                                            String value,
                                            int index) {
        String constantName = fieldNames.unique(constantName(value));
        classBuilder.addField(field -> field.name(constantName)
                .accessModifier(AccessModifier.PRIVATE)
                .type(int.class)
                .isStatic(true)
                .isFinal(true)
                .defaultValue(Integer.toString(index)));
        return constantName;
    }

    private static void addOrdinalCase(Method.Builder method, String value, boolean normalized, String constantName) {
        // The brace-ending chunk preserves case-body indentation when a comment follows.
        method.addContent("case " + constantName + " -> {");
        if (normalized || !constantName.equals(constantName(value))) {
            method.addContent(" // ").addContentLiteral(value);
        }
        method.addContentLine();
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
            method.addContent(creatorArguments(converterInfo, creatorInfo)).addContentLine(");");
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
        requiredFlags(method, jsonProperties);
        if (hasCreator) {
            creatorInfo.parameters()
                    .stream()
                    .map(parameterName -> creatorProperty(converterInfo, parameterName))
                    .forEach(jsonProperty -> addPropertyVariable(method, jsonProperty));
            jsonProperties.stream()
                    .filter(not(JsonProperty::usedInCreator))
                    .forEach(jsonProperty -> addPropertyVariable(method, jsonProperty));
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
                if (jsonProperty.usedInBuilder()) {
                    //This property is handled by the builder
                    continue;
                }
                addPropertyVariable(method, jsonProperty);
            }
        } else {
            TypeName originalType = converterInfo.objectsGenerics();
            method.addContent(originalType).addContent(" instance = new ")
                    .addContent(originalType).addContentLine("();");
        }
    }

    private static void requiredFlags(Method.Builder method, List<JsonProperty> properties) {
        properties.stream()
                .filter(JsonProperty::required)
                .map(JsonConverterGenerator::convertToMissingName)
                .forEach(name -> method.addContent(boolean.class).addContentLine(" " + name + " = true;"));
    }

    private static String convertToMissingName(JsonProperty jsonProperty) {
        return jsonProperty.identifier() + MISSING_SUFFIX;
    }

    private static String creatorArguments(ConvertedTypeInfo converterInfo, CreatorInfo creatorInfo) {
        return creatorInfo.parameters()
                .stream()
                .map(parameterName -> creatorProperty(converterInfo, parameterName).identifier()
                        + PROPERTY_NAME_SUFFIX)
                .collect(Collectors.joining(", "));
    }

    private static JsonProperty creatorProperty(ConvertedTypeInfo converterInfo, String parameterName) {
        JsonProperty property = converterInfo.jsonProperties().get(parameterName);
        if (property == null) {
            throw new IllegalStateException("Could not find JSON property for creator parameter: " + parameterName);
        }
        return property;
    }

    private static void addPropertyVariable(Method.Builder method, JsonProperty jsonProperty) {
        TypeName type = jsonProperty.deserializationType().orElseThrow();
        method.addContent(type)
                .addContent(" " + jsonProperty.identifier() + PROPERTY_NAME_SUFFIX + " = ")
                .addContentLine(DEFAULT_TYPE_VALUES.getOrDefault(type.genericTypeName(), DEFAULT_TYPE_VALUE).get() + ";");
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
        return GeneratedNames.normalize(result.toString());
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

    private static String deserializerField(JsonProperty property,
                                            ClassBase.Builder<?, ?> classBuilder,
                                            Set<String> processedTypes,
                                            Map<String, TypeToConfigure> toConfigure,
                                            GeneratedNames fieldNames) {
        if (property.deserializer().isPresent()) {
            return addUserDeserializer(property, property.deserializer().orElseThrow(), classBuilder, fieldNames);
        }
        return createTypeDeserializer(property, property.deserializationType().orElseThrow(), classBuilder, processedTypes,
                                      toConfigure, fieldNames);
    }

    private static String createTypeDeserializer(JsonProperty jsonProperty,
                                                 TypeName type,
                                                 ClassBase.Builder<?, ?> classBuilder,
                                                 Set<String> processedTypes,
                                                 Map<String, TypeToConfigure> toConfigure,
                                                 GeneratedNames fieldNames) {
        TypeName resolvedType = type.boxed();
        if (type.typeArguments().isEmpty()) {
            String converterFieldName = fieldNames.deserializer(type, "deserializer" + ensureUpperStart(type));
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
            return converterFieldName;
        } else {
            //Type contains generics
            String fieldName = fieldNames.unique("deserializer" + capitalize(jsonProperty.deserializationName().orElseThrow()));
            TypeName fieldType = TypeName.builder(JsonTypes.JSON_DESERIALIZER_TYPE).addTypeArgument(resolvedType).build();
            classBuilder.addField(builder -> builder.name(fieldName)
                    .isVolatile(true)
                    .type(fieldType));
            toConfigure.putIfAbsent(fieldName, new TypeToConfigure(TypeConfigMode.DESERIALIZATION,
                                                                   fieldName,
                                                                   resolvedType,
                                                                   type,
                                                                   fieldType));
            return fieldName;
        }
    }

    private static String addUserDeserializer(JsonProperty property,
                                              TypeName deserializer,
                                              ClassBase.Builder<?, ?> classBuilder,
                                              GeneratedNames fieldNames) {
        String constantName = fieldNames.unique(constantName(property.deserializationName().orElseThrow()) + "_DESERIALIZER");
        classBuilder.addField(field -> field.name(constantName)
                .type(deserializer)
                .isStatic(true)
                .isFinal(true)
                .addContent("new ").addContent(deserializer).addContent("()"));

        return constantName;
    }

    private static void valueWritingMethod(JsonProperty property,
                                           Method.Builder method,
                                           boolean hasCreator,
                                           boolean hasBuilder,
                                           String reference) {
        if (hasCreator || (hasBuilder && !property.usedInBuilder())) {
            String deserPropertyName = property.identifier();
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
        int dimensions = 0;
        TypeName componentType = typeName;
        while (componentType.array()) {
            dimensions++;
            Optional<TypeName> component = componentType.componentType();
            if (component.isEmpty()) {
                break;
            }
            componentType = component.orElseThrow();
        }
        String className = componentType.wildcard() ? TypeNames.OBJECT.className() : componentType.className();
        return capitalize(className) + "Array".repeat(dimensions);
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

    private record PropertyReader(JsonProperty property, String constantName, String deserializer) {
    }
}
