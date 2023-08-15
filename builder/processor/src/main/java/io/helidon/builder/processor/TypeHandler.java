/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.common.processor.classmodel.ClassModel;
import io.helidon.common.processor.classmodel.Field;
import io.helidon.common.processor.classmodel.InnerClass;
import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.processor.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.builder.processor.Types.ARRAY_LIST_TYPE;
import static io.helidon.builder.processor.Types.CHAR_ARRAY_TYPE;
import static io.helidon.builder.processor.Types.DURATION_TYPE;
import static io.helidon.builder.processor.Types.LINKED_HASH_MAP_TYPE;
import static io.helidon.builder.processor.Types.LINKED_HASH_SET_TYPE;
import static io.helidon.builder.processor.Types.STRING_TYPE;

class TypeHandler {
    static final String UNCONFIGURED = "io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED";
    private final String name;
    private final String getterName;
    private final String setterName;
    private final TypeName declaredType;

    TypeHandler(String name, String getterName, String setterName, TypeName declaredType) {
        this.name = name;
        this.getterName = getterName;
        this.setterName = setterName;
        this.declaredType = declaredType;
    }

    static TypeHandler create(String name, String getterName, String setterName, TypeName returnType, boolean sameGeneric) {
        if (TypeNames.OPTIONAL.equals(returnType)) {
            return new TypeHandlerOptional(name, getterName, setterName, returnType);
        }
        if (TypeNames.SET.equals(returnType)) {
            return new TypeHandlerSet(name, getterName, setterName, returnType);
        }

        if (TypeNames.LIST.equals(returnType)) {
            return new TypeHandlerList(name, getterName, setterName, returnType);
        }
        if (TypeNames.MAP.equals(returnType)) {
            return new TypeHandlerMap(name, getterName, setterName, returnType, sameGeneric);
        }

        return new TypeHandler(name, getterName, setterName, returnType);
    }

    static AccessModifier setterAccessModifier(PrototypeProperty.ConfiguredOption configured) {
        if (configured.builderMethod()) {
            return AccessModifier.PUBLIC;
        }
        return AccessModifier.PROTECTED;
    }

    static TypeName toWildcard(TypeName typeName) {
        if (typeName.wildcard()) {
            return typeName;
        }
        return TypeName.builder(typeName).wildcard(true).build();
    }

    protected static TypeName collectionImplType(TypeName typeName) {
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(TypeNames.MAP)) {
            return LINKED_HASH_MAP_TYPE;
        }
        if (genericTypeName.equals(TypeNames.LIST)) {
            return ARRAY_LIST_TYPE;
        }

        return LINKED_HASH_SET_TYPE;
    }

    @Override
    public String toString() {
        return declaredType.fqName() + " " + name;
    }

    TypeName builderGetterType(boolean required, boolean hasDefault) {
        if (builderGetterOptional(required, hasDefault)) {
            if (declaredType().isOptional()) {
                // already wrapped
                return declaredType();
            } else {
                return TypeName.builder(TypeNames.OPTIONAL)
                        .addTypeArgument(declaredType().boxed())
                        .build();
            }
        }
        return declaredType();
    }

    String generateBuilderGetter(boolean required, boolean hasDefault) {
        if (builderGetterOptional(required, hasDefault)) {
            return ClassModel.TYPE_TOKEN + "java.util.Optional" + ClassModel.TYPE_TOKEN + ".ofNullable(" + name + ")";
        } else {
            return name;
        }
    }

    Field.Builder fieldDeclaration(PrototypeProperty.ConfiguredOption configured,
                                   boolean isBuilder,
                                   boolean alwaysFinal) {
        Field.Builder builder = Field.builder()
                .name(name)
                .isFinal(alwaysFinal || !isBuilder);

        if (isBuilder && (configured.required())) {
            // we need to use object types to be able to see if this was configured
            builder.type(declaredType.boxed());
        } else {
            builder.type(declaredType);
        }

        if (isBuilder && configured.hasDefault()) {
            builder.defaultValue(configured.defaultValue());
        }

        return builder;
    }

    String toDefaultValue(String defaultValue) {
        TypeName typeName = actualType();
        if (STRING_TYPE.equals(typeName)) {
            return "\"" + defaultValue + "\"";
        }
        if (DURATION_TYPE.equals(typeName)) {
            return ClassModel.TYPE_TOKEN + "java.time.Duration" + ClassModel.TYPE_TOKEN + ".parse(\"" + defaultValue + "\")";
        }
        if (CHAR_ARRAY_TYPE.equals(typeName)) {
            return "\"" + defaultValue + "\".toCharArray()";
        }
        if (typeName.primitive()) {
            if (typeName.fqName().equals("char")) {
                return "'" + defaultValue + "'";
            }
            return defaultValue;
        }
        if (typeName.name().startsWith("java.")) {
            return defaultValue;
        }
        // should be an enum
        return ClassModel.TYPE_TOKEN + typeName.genericTypeName().fqName() + ClassModel.TYPE_TOKEN + "." + defaultValue;
    }

    TypeName declaredType() {
        return declaredType;
    }

    TypeName actualType() {
        return declaredType;
    }

    String name() {
        return name;
    }

    String getterName() {
        return getterName;
    }

    String setterName() {
        return setterName;
    }

    void generateFromConfig(Method.Builder method,
                            PrototypeProperty.ConfiguredOption configured,
                            FactoryMethods factoryMethods) {
        method.add(configGet(configured));
        generateFromConfig(method, factoryMethods);
        method.addLine(".ifPresent(this::" + setterName() + ");");
    }

    String configGet(PrototypeProperty.ConfiguredOption configured) {
        return "config.get(\"" + configured.configKey() + "\")";
    }

    String generateFromConfig(FactoryMethods factoryMethods) {
        if (actualType().fqName().equals("char[]")) {
            return ".asString().map(String::toCharArray)";
        }

        TypeName boxed = actualType().boxed();
        return factoryMethods.createFromConfig()
                .map(it -> ".map(" + it.typeWithFactoryMethod().genericTypeName().fqName() + "::" + it.createMethodName() + ")")
                .orElseGet(() -> ".as(" + boxed.fqName() + ".class)");

    }

    void generateFromConfig(Method.Builder method, FactoryMethods factoryMethods) {
        if (actualType().fqName().equals("char[]")) {
            method.add(".asString().map(").typeName(String.class).add("::toCharArray)");
            return;
        }

        Optional<FactoryMethods.FactoryMethod> fromConfig = factoryMethods.createFromConfig();
        if (fromConfig.isPresent()) {
            FactoryMethods.FactoryMethod factoryMethod = fromConfig.get();
            method.add(".map(")
                    .typeName(factoryMethod.typeWithFactoryMethod().genericTypeName())
                    .add("::" + factoryMethod.createMethodName() + ")");
        } else {
            TypeName boxed = actualType().boxed();
            method.add(".as(").typeName(boxed).add(".class)");
        }
    }

    TypeName argumentTypeName() {
        return declaredType();
    }

    void setters(InnerClass.Builder classBuilder,
                 PrototypeProperty.ConfiguredOption configured,
                 PrototypeProperty.Singular singular,
                 FactoryMethods factoryMethod,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        declaredSetter(classBuilder, configured, returnType, blueprintJavadoc);

        if (actualType().equals(CHAR_ARRAY_TYPE)) {
            charArraySetter(classBuilder, configured, returnType, blueprintJavadoc);
        }

        // if there is a factory method for the return type, we also have setters for the type (probably config object)
        if (factoryMethod.createTargetType().isPresent()) {
            factorySetter(classBuilder, configured, returnType, blueprintJavadoc, factoryMethod.createTargetType().get());
        }

        // if there is a builder factory method, we create a method with builder consumer
        if (factoryMethod.builder().isPresent()) {
            factorySetterConsumer(classBuilder, configured, returnType, blueprintJavadoc, factoryMethod.builder().get());
            factorySetterSupplier(classBuilder, configured, returnType, blueprintJavadoc);
        }
    }

    protected void charArraySetter(InnerClass.Builder classBuilder,
                                   PrototypeProperty.ConfiguredOption configured,
                                   TypeName returnType,
                                   Javadoc blueprintJavadoc) {
        classBuilder.addMethod(builder -> builder.name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(name())
                        .type(STRING_TYPE)
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class).addLine(".requireNonNull(" + name() + ");")
                .addLine("this." + name() + " = " + name() + ".toCharArray();")
                .addLine("return self();"));
    }

    boolean builderGetterOptional(boolean required, boolean hasDefault) {
        // optional and collections - good return types
        if (declaredType().isList()
                || declaredType().isMap()
                || declaredType().isSet()) {
            return false;
        }
        if (declaredType().isOptional()) {
            return true;
        }
        // optional and primitive type - good return type (uses default for primitive if not customized)
        if (!required && declaredType().primitive()) {
            return false;
        }
        // has default, and not Optional<X> - return type (never can be null)
        // any other case (required, optional without defaults) - return optional
        return !hasDefault;

    }

    private void declaredSetter(InnerClass.Builder classBuilder,
                                PrototypeProperty.ConfiguredOption configured,
                                TypeName returnType,
                                Javadoc blueprintJavadoc) {
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured));
        if (!declaredType.primitive()) {
            builder.typeName(Objects.class).addLine(".requireNonNull(" + name() + ");");
        }
        builder.addLine("this." + name() + " = " + name() + ";")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void factorySetterConsumer(InnerClass.Builder classBuilder,
                                       PrototypeProperty.ConfiguredOption configured,
                                       TypeName returnType,
                                       Javadoc blueprintJavadoc,
                                       FactoryMethods.FactoryMethod factoryMethod) {
        TypeName builderType;
        if (factoryMethod.factoryMethodReturnType().className().equals("Builder")) {
            builderType = factoryMethod.factoryMethodReturnType();
        } else {
            builderType = TypeName.create(factoryMethod.factoryMethodReturnType().fqName() + ".Builder");
        }

        String argumentName = "consumer";

        List<String> paramLines = new ArrayList<>();
        paramLines.add("consumer of builder for");
        paramLines.addAll(blueprintJavadoc.returnDescription());

        TypeName argumentType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(builderType)
                .build();
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType)
                        .description(paramLines))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + argumentName + ");")
                .add("var builder = ")
                .typeName(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addLine("." + factoryMethod.createMethodName() + "();")
                .addLine("consumer.accept(builder);")
                .addLine("this." + name() + "(builder.build());")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void factorySetterSupplier(InnerClass.Builder classBuilder,
                                       PrototypeProperty.ConfiguredOption configured,
                                       TypeName returnType,
                                       Javadoc blueprintJavadoc) {
        TypeName supplierType = actualType();
        if (!supplierType.wildcard()) {
            supplierType = TypeName.builder(supplierType)
                    .wildcard(true)
                    .build();
        }
        supplierType = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(supplierType)
                .build();

        String argumentName = "supplier";

        List<String> paramLines = new ArrayList<>();
        paramLines.add("supplier of");
        paramLines.addAll(blueprintJavadoc.returnDescription());

        TypeName argumentType = supplierType;
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType)
                        .description(paramLines))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + argumentName + ");")
                .addLine("this." + name() + "(" + argumentName + ".get());")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void factorySetter(InnerClass.Builder classBuilder,
                               PrototypeProperty.ConfiguredOption configured,
                               TypeName returnType,
                               Javadoc blueprintJavadoc,
                               FactoryMethods.FactoryMethod factoryMethod) {
        String argumentName = name() + "Config";
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(argumentName)
                        .type(factoryMethod.argumentType())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class).addLine(".requireNonNull(" + argumentName + ");")
                .add("this." + name() + " = ")
                .typeName(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addLine("." + factoryMethod.createMethodName() + "(" + argumentName + ");")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    static class OneTypeHandler extends TypeHandler {
        private final TypeName actualType;

        OneTypeHandler(String name, String getterName, String setterName, TypeName declaredType) {
            super(name, getterName, setterName, declaredType);

            if (declaredType.typeArguments().isEmpty()) {
                this.actualType = STRING_TYPE;
            } else {
                this.actualType = declaredType.typeArguments().get(0);
            }
        }

        @Override
        TypeName actualType() {
            return actualType;
        }
    }
}
