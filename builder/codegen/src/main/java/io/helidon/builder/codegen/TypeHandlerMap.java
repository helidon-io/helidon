/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.ARRAY_LIST;
import static io.helidon.builder.codegen.Types.LINKED_HASH_MAP;
import static io.helidon.builder.codegen.Types.LINKED_HASH_SET;
import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.OBJECT;
import static io.helidon.common.types.TypeNames.SET;

class TypeHandlerMap extends TypeHandlerContainer {
    private static final TypeName SAME_GENERIC_TYPE = TypeName.createFromGenericDeclaration("TYPE");

    private final boolean sameGeneric;
    private final TypeName keyType;
    private final TypeName valueType;

    TypeHandlerMap(List<BuilderCodegenExtension> extensions, PrototypeInfo prototypeInfo, OptionInfo option) {
        super(extensions, prototypeInfo, option, option.declaredType().typeArguments().get(1));

        this.sameGeneric = option.sameGeneric();
        this.keyType = option.declaredType().typeArguments().get(0);
        this.valueType = option.declaredType().typeArguments().get(1);
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        String optionName = option().name();

        if (TypeNames.STRING.equals(keyType) && TypeNames.STRING.equals(valueType)) {
            // the special case of Map<String, String>
            method.addContentLine(configGet(optionConfigured) + ".detach().asMap().ifPresent(this::" + optionName + ");");
        } else if (optionConfigured.traverse()) {
            method.addContent(configGet(optionConfigured) + ".detach().traverse().filter(")
                    .addContent(Types.COMMON_CONFIG)
                    .addContent("::hasValue).forEach(node -> "
                                        + optionName + ".put(node.get(\"name\").asString().orElse(node.key().toString()), node");
            generateFromConfig(method);
            method.addContentLine(".get()));");
        } else {
            method.addContent(configGet(optionConfigured)
                                      + ".asNodeList().ifPresent(nodes -> nodes.forEach"
                                      + "(node -> "
                                      + optionName + ".put(node.get(\"name\").asString().orElse(node.name()), node");
            if (optionConfigured.factoryMethod().isPresent()) {
                generateFromConfig(method, optionConfigured.factoryMethod().get());
            } else {
                generateFromConfig(method);
            }
            method.addContentLine(".get())));");
        }
    }

    @Override
    GeneratedMethod prepareBuilderSetter(Javadoc getterJavadoc) {
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        Javadoc javadoc = Javadoc.builder(setterJavadoc(getterJavadoc, name, ""))
                .add("\nThis method replaces all values with the new ones.")
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(typeOfMap(keyType, valueType))
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");");

            it.addContentLine("this." + name + ".clear();")
                    .addContentLine("this." + name + ".putAll(" + name + ");");
            extraSetterContent(it);
            it.addContentLine("return self();");
        };

        return GeneratedMethod.builder()
                .method(method.build())
                .javadoc(javadoc)
                .contentBuilder(contentConsumer)
                .build();
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderAddCollection(Javadoc getterJavadoc) {
        TypeName returnType = Utils.builderReturnType();
        String name = option().name();

        Javadoc javadoc = Javadoc.builder(setterJavadoc(getterJavadoc, name, ""))
                .add("\nThis method keeps existing values, then puts all new values into the map.")
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName("add" + capitalize(name))
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(typeOfMap(keyType, valueType))
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");")
                    .addContentLine("this." + name + ".putAll(" + name + ");");
            extraSetterContent(it);
            it.addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .javadoc(javadoc)
                                           .contentBuilder(contentConsumer)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSingularAdd(Javadoc getterJavadoc) {
        if (option().singular().isEmpty()) {
            return Optional.empty();
        }
        OptionSingular singular = option().singular().get();
        String singularName = singular.name();
        String methodName = singular.methodName();
        TypeName returnType = Utils.builderReturnType();
        String name = option().name();

        Javadoc setterJavadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "key", ""))
                .add("\nThis method adds a new value to the map, or replaces it if the key already exists.")
                .parameters(Map.of())
                .addParameter("key", "key to add or replace")
                .addParameter(singularName, "new value for the key")
                .update(it -> {
                    if (sameGeneric) {
                        it.addGenericArgument("TYPE", "The key and value has to use the same generic type.");
                    }
                })
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(methodName)
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        if (sameGeneric) {
            sameGenericArgs(method, keyType, singularName, type());
        } else {
            method.addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(keyType)
                            .elementName("key"))
                    .addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(type())
                            .elementName(singularName)
                    );
        }

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(key);")
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + singularName + ");")
                    .addContent("this." + name + ".put(key, ");
            secondArgToPut(it, type(), singularName);
            it.addContentLine(");");
            extraAdderContent(it);
            it.addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .contentBuilder(contentConsumer)
                                           .javadoc(setterJavadoc)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSingularAddToMapValue(Javadoc getterJavadoc) {
        if (option().singular().isEmpty()) {
            return Optional.empty();
        }
        if (!(valueType.isSet() || valueType.isList())) {
            return Optional.empty();
        }
        OptionSingular singular = option().singular().get();
        String singularName = singular.name();
        String methodName = "add" + capitalize(singularName);
        TypeName returnType = Utils.builderReturnType();
        TypeName valueParamType = type().typeArguments().getFirst();

        Javadoc setterJavadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "key", ""))
                .add("\nThis method adds a new value to the map value, or creates a new value.")
                .parameters(Map.of())
                .addParameter("key", "key to add value for")
                .addParameter(singularName, "value to add to the map values")
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(methodName)
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        if (sameGeneric) {
            sameGenericArgs(method, keyType, singularName, valueParamType);
        } else {
            method.addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(keyType)
                            .elementName("key"))
                    .addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(valueParamType)
                            .elementName(singularName)
                    );
        }

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(key);")
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + singularName + ");")
                    .addContentLine("this." + option().name() + ".compute(key, (k, v) -> {")
                    .addContent("v = v == null ? new ")
                    .addContent(containerValueTypeImpl())
                    .addContent("<>() : new ")
                    .addContent(containerValueTypeImpl())
                    .addContentLine("<>(v);")
                    .addContentLine("v.add(" + singularName + ");")
                    .addContentLine("return v;")
                    .decreaseContentPadding()
                    .addContentLine("});");

            extraAdderContent(it);
            it.addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .contentBuilder(contentConsumer)
                                           .javadoc(setterJavadoc)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSingularAddToMapValues(Javadoc getterJavadoc) {
        if (option().singular().isEmpty()) {
            return Optional.empty();
        }
        if (!(valueType.isSet() || valueType.isList())) {
            return Optional.empty();
        }
        String methodName = "add" + capitalize(option().name());
        TypeName returnType = Utils.builderReturnType();
        String name = option().name();

        Javadoc setterJavadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "key", ""))
                .add("\nThis method adds new values to the map values, or creates a new mapping.")
                .parameters(Map.of())
                .addParameter("key", "key to add value for")
                .addParameter(name, "values to add to the map values")
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(methodName)
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        if (sameGeneric) {
            sameGenericArgs(method, keyType, name, type());
        } else {
            method.addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(keyType)
                            .elementName("key"))
                    .addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(type())
                            .elementName(name)
                    );
        }

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(key);")
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");")
                    .addContentLine("this." + option().name() + ".compute(key, (k, v) -> {")
                    .addContent("v = v == null ? new ")
                    .addContent(containerValueTypeImpl())
                    .addContent("<>() : new ")
                    .addContent(containerValueTypeImpl())
                    .addContentLine("<>(v);")
                    .addContentLine("v.addAll(" + name + ");")
                    .addContentLine("return v;")
                    .decreaseContentPadding()
                    .addContentLine("});");

            extraAdderContent(it);
            it.addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .contentBuilder(contentConsumer)
                                           .javadoc(setterJavadoc)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSingularAddConsumer(Javadoc getterJavadoc) {
        if (option().singular().isEmpty()
                || (option().builderInfo().isEmpty() && option().runtimeType().isEmpty())) {
            return Optional.empty();
        }

        OptionSingular singular = option().singular().get();
        String methodName = singular.methodName();
        TypeName returnType = Utils.builderReturnType();

        /*
        builder.putOption("key", builder -> builder.port(49))
         */
        if (option().runtimeType().isPresent()) {
            RuntimeTypeInfo rti = option().runtimeType().get();
            var optionBuilder = rti.optionBuilder();
            var factoryMethod = rti.factoryMethod();

            Javadoc setterJavadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "key", ""))
                    .add("\nThis method adds a new value to the map, or replaces it if the key already exists.")
                    .parameters(Map.of())
                    .addParameter("key", "key to add or replace")
                    .addParameter("consumer", "consumer of builder for new value")
                    .build();

            TypeName paramType = TypeName.builder()
                    .type(Consumer.class)
                    .addTypeArgument(optionBuilder.builderType())
                    .build();

            var method = TypedElementInfo.builder()
                    .kind(ElementKind.METHOD)
                    .accessModifier(option().accessModifier())
                    .typeName(returnType)
                    .elementName(methodName)
                    .update(this::deprecation)
                    .update(it -> option().annotations().forEach(it::addAnnotation));

            method.addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(keyType)
                            .elementName("key"))
                    .addParameterArgument(param -> param
                            .kind(ElementKind.PARAMETER)
                            .typeName(paramType)
                            .elementName("consumer")
                    );

            Consumer<ContentBuilder<?>> contentConsumer = it -> {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(consumer);")
                        .addContent("var builder = ");

                if (optionBuilder.builderMethodName().equals("<init>")) {
                    it.addContent("new ")
                            .addContent(optionBuilder.builderType())
                            .addContentLine("();");
                } else {
                    it.addContent(optionBuilder.builderMethodType())
                            .addContentLine("." + optionBuilder.builderMethodName() + "();");
                }

                it.addContentLine("consumer.accept(builder);")
                        .addContent("this." + methodName + "(key, ");

                factoryMethod.ifPresent(fm -> it.addContent(fm.declaringType().genericTypeName())
                        .addContent(".")
                        .addContent(fm.methodName())
                        .addContent("("));

                it.addContent("builder.")
                        .addContent(optionBuilder.buildMethodName())
                        .addContent("()");

                factoryMethod.ifPresent(f -> it.addContent(")"));

                it.addContentLine(");");
                it.addContentLine("return self();");
            };

            return Optional.of(GeneratedMethod.builder()
                                       .method(method.build())
                                       .javadoc(setterJavadoc)
                                       .contentBuilder(contentConsumer)
                                       .build());
        }
        var optionBuilder = option().builderInfo().get();

        Javadoc setterJavadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "key", ""))
                .add("\nThis method adds a new value to the map, or replaces it if the key already exists.")
                .parameters(Map.of())
                .addParameter("key", "key to add or replace")
                .addParameter("consumer", "consumer of builder for new value")
                .build();

        TypeName paramType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(optionBuilder.builderType())
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(methodName)
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                        .kind(ElementKind.PARAMETER)
                        .typeName(keyType)
                        .elementName("key"))
                .addParameterArgument(param -> param
                        .kind(ElementKind.PARAMETER)
                        .typeName(paramType)
                        .elementName("consumer")
                );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(consumer);")
                    .addContent("var builder = ");

            if (optionBuilder.builderMethodName().equals("<init>")) {
                it.addContent("new ")
                        .addContent(optionBuilder.builderType())
                        .addContentLine("();");
            } else {
                it.addContent(optionBuilder.builderMethodType())
                        .addContentLine("." + optionBuilder.builderMethodName() + "();");
            }

            it.addContentLine("consumer.accept(builder);")
                    .addContent("this." + methodName + "(key, ");

            it.addContent("builder.")
                    .addContent(optionBuilder.buildMethodName())
                    .addContent("()");

            it.addContentLine(");");
            it.addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(setterJavadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    @Override
    void addFields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder) {
        Field.Builder builder = super.field(isBuilder);
        if (isBuilder && option().defaultValue().isEmpty()) {
            builder.addContent("new ")
                    .addContent(LINKED_HASH_MAP)
                    .addContent("<>()");
        }

        classBuilder.addField(builder.build());
    }

    private TypeName containerValueTypeImpl() {
        if (valueType.isList()) {
            return ARRAY_LIST;
        }
        if (valueType.isSet()) {
            return LINKED_HASH_SET;
        }
        if (valueType.isMap()) {
            return LINKED_HASH_MAP;
        }
        return valueType;
    }

    private TypeName typeOfMap(TypeName keyType, TypeName valueType) {
        return TypeName.builder(MAP)
                .addTypeArgument(Utils.toWildcard(keyType))
                .addTypeArgument(Utils.toWildcard(valueType))
                .build();
    }

    private void sameGenericArgs(TypedElementInfo.Builder method,
                                 TypeName keyType,
                                 String value,
                                 TypeName valueType) {

        TypeName genericTypeBase;
        TypeName resolvedKeyType;
        TypeName resolvedValueType;

        if (keyType.typeArguments().isEmpty()) {
            /*
            Map<Object, List<Object>>
            <TYPE extends Object> put(TYPE, List<TYPE>)
             */
            // this is good
            genericTypeBase = keyType;
            resolvedKeyType = SAME_GENERIC_TYPE;
        } else if (keyType.typeArguments().size() == 1) {
            /*
            Map<Class<Provider>, Provider>
            <TYPE extends Provider> put(Class<TYPE>, List<TYPE>)
             */
            // this is also good
            TypeName typeArg = keyType.typeArguments().getFirst();
            if (typeArg.wildcard()) {
                // ?, or ? extends Something
                if (typeArg.generic()) {
                    genericTypeBase = OBJECT;
                } else {
                    genericTypeBase = TypeName.builder(typeArg)
                            .wildcard(false)
                            .build();
                }
            } else {
                genericTypeBase = typeArg;
            }
            resolvedKeyType = TypeName.builder(keyType)
                    .typeArguments(List.of(SAME_GENERIC_TYPE))
                    .build();
        } else {
            throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                    .fqName() + " is annotated"
                                                       + " with @SameGeneric, yet the key generic type cannot be determined."
                                                       + " Either the key must be a simple type, or a type with one type"
                                                       + " argument.");
        }

        method.addTypeParameter(TypeArgument.builder()
                                        .token("TYPE")
                                        .bound(genericTypeBase)
                                        .description("Type to correctly map key and value")
                                        .build());

        // now resolve value
        if (valueType.typeArguments().isEmpty()) {
            if (!genericTypeBase.equals(valueType)) {
                throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                        .fqName() + " is "
                                                           + "annotated"
                                                           + " with @SameGeneric, yet the type of value is not the"
                                                           + " same as type found on key: " + genericTypeBase.fqName());
            }
            resolvedValueType = SAME_GENERIC_TYPE;
        } else if (valueType.typeArguments().size() == 1) {
            if (!genericTypeBase.equals(valueType.typeArguments().getFirst())) {
                throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                        .fqName() + " is "
                                                           + "annotated"
                                                           + " with @SameGeneric, yet type of value is not the"
                                                           + " same as type found on key: " + genericTypeBase.fqName());
            }
            resolvedValueType = TypeName.builder(valueType)
                    .typeArguments(List.of(SAME_GENERIC_TYPE))
                    .build();
        } else {
            throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                    .fqName() + " is annotated"
                                                       + " with @SameGeneric, yet the value generic type cannot be determined."
                                                       + " Either the value must be a simple type, or a type with one type"
                                                       + " argument.");
        }

        method.addParameterArgument(param -> param.elementName("key")
                        .kind(ElementKind.PARAMETER)
                        .typeName(resolvedKeyType)
                        .description("key to add or replace"))
                .addParameterArgument(param -> param.elementName(value)
                        .kind(ElementKind.PARAMETER)
                        .typeName(resolvedValueType)
                        .description("new value for the key"));
    }

    private void secondArgToPut(ContentBuilder<?> contentBuilder, TypeName typeName, String singularName) {
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(LIST)) {
            contentBuilder.addContent(List.class)
                    .addContent(".copyOf(" + singularName + ")");
        } else if (genericTypeName.equals(SET)) {
            contentBuilder.addContent(Set.class)
                    .addContent(".copyOf(" + singularName + ")");
        } else if (genericTypeName.equals(MAP)) {
            contentBuilder.addContent(Map.class)
                    .addContent(".copyOf(" + singularName + ")");
        } else {
            contentBuilder.addContent(singularName);
        }
    }
}
