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
import static io.helidon.common.processor.classmodel.ClassModel.TYPE_TOKEN;

class TypeHandler {
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
        if (TypeNames.SUPPLIER.equals(returnType)) {
            return new TypeHandlerSupplier(name, getterName, setterName, returnType);
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

    static AccessModifier setterAccessModifier(AnnotationDataOption configured) {
        return configured.accessModifier();
    }

    static TypeName toWildcard(TypeName typeName) {
        if (typeName.wildcard()) {
            return typeName;
        }
        if (typeName.equals(TypeNames.STRING)) {
            return typeName;
        }
        if (typeName.typeArguments().isEmpty()) {
            return TypeName.builder(typeName).wildcard(true).build();
        }
        return typeName;
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
            return TYPE_TOKEN + "java.util.Optional" + TYPE_TOKEN + ".ofNullable(" + name + ")";
        } else {
            return name;
        }
    }

    Field.Builder fieldDeclaration(AnnotationDataOption configured,
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
            builder.defaultValueContent(configured.defaultValue());
        }

        return builder;
    }

    String toDefaultValue(String defaultValue) {
        TypeName typeName = actualType();
        if (STRING_TYPE.equals(typeName)) {
            return "\"" + defaultValue + "\"";
        }
        if (DURATION_TYPE.equals(typeName)) {
            return TYPE_TOKEN + "java.time.Duration" + TYPE_TOKEN + ".parse(\"" + defaultValue + "\")";
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
        return TYPE_TOKEN + typeName.genericTypeName().fqName() + TYPE_TOKEN + "." + defaultValue;
    }

    String toDefaultValue(List<String> defaultValues,
                          List<Integer> defaultInts,
                          List<Long> defaultLongs,
                          List<Double> defaultDoubles,
                          List<Boolean> defaultBooleans,
                          String defaultCode,
                          AnnotationDataOption.DefaultMethod defaultMethod) {
        if (defaultCode != null) {
            return defaultCode;
        }
        if (defaultMethod != null) {
            // must return the correct type
            return toDefaultFromMethod(defaultMethod);
        }

        return toDefaultValue(defaultValues,
                              defaultInts,
                              defaultLongs,
                              defaultDoubles,
                              defaultBooleans);
    }

    String toDefaultValue(List<String> defaultValues,
                          List<Integer> defaultInts,
                          List<Long> defaultLongs,
                          List<Double> defaultDoubles,
                          List<Boolean> defaultBooleans) {
        if (defaultValues != null) {
            String string = singleDefault(defaultValues);
            return toDefaultValue(string);
        }
        if (defaultInts != null) {
            return String.valueOf(singleDefault(defaultInts));
        }
        if (defaultLongs != null) {
            return singleDefault(defaultLongs) + "L";
        }
        if (defaultDoubles != null) {
            return String.valueOf(singleDefault(defaultDoubles));
        }
        if (defaultBooleans != null) {
            return String.valueOf(singleDefault(defaultBooleans));
        }

        return null;
    }

    protected String toDefaultFromMethod(AnnotationDataOption.DefaultMethod defaultMethod) {
        return TYPE_TOKEN + defaultMethod.type().fqName() + TYPE_TOKEN + "." + defaultMethod.method() + "()";
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
                            AnnotationDataOption configured,
                            FactoryMethods factoryMethods) {
        method.add(configGet(configured));
        String fqName = actualType().fqName();

        if (fqName.endsWith(".Builder")) {
            // this is a special case - we have a builder field
            if (configured.hasDefault()) {
                method.addLine(".as(Config.class).ifPresent(" + name() + "::config);");
            } else {
                // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
                int lastDot = fqName.lastIndexOf('.');
                String builderMethod = fqName.substring(0, lastDot) + ".builder()";
                method.addLine(".map(" + builderMethod + "::config).ifPresent(this::" + setterName() + ");");
            }
        } else {
            generateFromConfig(method, factoryMethods);
            method.addLine(".ifPresent(this::" + setterName() + ");");
        }
    }

    String configGet(AnnotationDataOption configured) {
        if (configured.configMerge()) {
            return "config";
        }
        return "config.get(\"" + configured.configKey() + "\")";
    }

    String generateFromConfig(FactoryMethods factoryMethods) {
        if (actualType().fqName().equals("char[]")) {
            return ".asString().as(String::toCharArray)";
        }

        TypeName boxed = actualType().boxed();
        return factoryMethods.createFromConfig()
                .map(it -> ".map(" + it.typeWithFactoryMethod().genericTypeName().fqName() + "::" + it.createMethodName() + ")")
                .orElseGet(() -> ".as(" + boxed.fqName() + ".class)");

    }

    void generateFromConfig(Method.Builder method, FactoryMethods factoryMethods) {
        if (actualType().fqName().equals("char[]")) {
            method.add(".asString().as(").typeName(String.class).add("::toCharArray)");
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
                 AnnotationDataOption configured,
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

        String fqName = actualType().fqName();
        if (fqName.endsWith(".Builder")) {
            // this is a special case - we have a builder field, we want to generate consumer (special, same instance)
            setterConsumer(classBuilder, configured, returnType, blueprintJavadoc);
        }
    }

    void setterConsumer(InnerClass.Builder classBuilder,
                        AnnotationDataOption configured,
                        TypeName returnType,
                        Javadoc blueprintJavadoc) {
        String argumentName = "consumer";

        List<String> paramLines = new ArrayList<>();
        paramLines.add("consumer of builder for");
        paramLines.addAll(blueprintJavadoc.returnDescription());

        Javadoc javadoc = setterJavadoc(blueprintJavadoc)
                .addParameter(argumentName, paramLines)
                .build();

        TypeName argumentType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(actualType())
                .build();
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType)
                .update(it -> configured.annotations().forEach(it::addAnnotation))
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType))
                .javadoc(javadoc)
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + argumentName + ");")
                .add("var builder = ");

        if (configured.hasDefault()) {
            builder.addLine("this." + name() + ";");
        } else {
            String fqName = actualType().fqName();
            // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
            int lastDot = fqName.lastIndexOf('.');
            String builderMethod = fqName.substring(0, lastDot) + ".builder()";
            builder.addLine(builderMethod + ";");
        }

        builder.addLine("consumer.accept(builder);")
                .addLine("this." + name() + "(builder);")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    protected Javadoc.Builder setterJavadoc(Javadoc blueprintJavadoc) {
        return Javadoc.builder(blueprintJavadoc)
                .addTag("see", "#" + getterName() + "()")
                .returnDescription("updated builder instance");
    }

    protected void charArraySetter(InnerClass.Builder classBuilder,
                                   AnnotationDataOption configured,
                                   TypeName returnType,
                                   Javadoc blueprintJavadoc) {

        classBuilder.addMethod(builder -> builder.name(setterName())
                .returnType(returnType)
                .javadoc(setterJavadoc(blueprintJavadoc)
                                 .addParameter(name(), blueprintJavadoc.returnDescription())
                                 .build())
                .returnType(returnType)
                .addParameter(param -> param.name(name())
                        .type(STRING_TYPE))
                .accessModifier(setterAccessModifier(configured))
                .update(it -> configured.annotations().forEach(it::addAnnotation))
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

    protected void declaredSetter(InnerClass.Builder classBuilder,
                                  AnnotationDataOption configured,
                                  TypeName returnType,
                                  Javadoc blueprintJavadoc) {

        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType)
                .javadoc(setterJavadoc(blueprintJavadoc)
                                 .addParameter(name(), blueprintJavadoc.returnDescription())
                                 .build())
                .returnType(returnType)
                .update(it -> configured.annotations().forEach(it::addAnnotation))
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName()))
                .accessModifier(setterAccessModifier(configured));

        if (!declaredType.primitive()) {
            builder.typeName(Objects.class).addLine(".requireNonNull(" + name() + ");");
        }
        builder.addLine("this." + name() + " = " + name() + ";")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private <T> T singleDefault(List<T> defaultValues) {
        if (defaultValues.isEmpty()) {
            throw new IllegalArgumentException("Default values configured for " + name() + " are empty, one value is expected.");
        }
        if (defaultValues.size() > 1) {
            throw new IllegalArgumentException("Default values configured for " + name() + " contain more than one value,"
                                                       + " exactly one value is expected.");
        }
        return defaultValues.get(0);
    }

    private void factorySetterConsumer(InnerClass.Builder classBuilder,
                                       AnnotationDataOption configured,
                                       TypeName returnType,
                                       Javadoc blueprintJavadoc,
                                       FactoryMethods.FactoryMethod factoryMethod) {
        TypeName builderType;
        if (factoryMethod.factoryMethodReturnType().className().equals("Builder")) {
            builderType = factoryMethod.factoryMethodReturnType();
        } else if (factoryMethod.factoryMethodReturnType().className().endsWith(".Builder")) {
            builderType = factoryMethod.factoryMethodReturnType();
        } else {
            builderType = TypeName.create(factoryMethod.factoryMethodReturnType().fqName() + ".Builder");
        }

        String argumentName = "consumer";

        List<String> paramLines = new ArrayList<>();
        paramLines.add("consumer of builder for");
        paramLines.addAll(blueprintJavadoc.returnDescription());

        Javadoc javadoc = setterJavadoc(blueprintJavadoc)
                .addParameter(argumentName, paramLines)
                .build();

        TypeName argumentType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(builderType)
                .build();
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType)
                .update(it -> configured.annotations().forEach(it::addAnnotation))
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType))
                .accessModifier(setterAccessModifier(configured))
                .javadoc(javadoc)
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
                                       AnnotationDataOption configured,
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

        Javadoc javadoc = setterJavadoc(blueprintJavadoc)
                .addParameter(argumentName, paramLines)
                .build();

        TypeName argumentType = supplierType;
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType)
                .update(it -> configured.annotations().forEach(it::addAnnotation))
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType))
                .accessModifier(setterAccessModifier(configured))
                .javadoc(javadoc)
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + argumentName + ");")
                .addLine("this." + name() + "(" + argumentName + ".get());")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void factorySetter(InnerClass.Builder classBuilder,
                               AnnotationDataOption configured,
                               TypeName returnType,
                               Javadoc blueprintJavadoc,
                               FactoryMethods.FactoryMethod factoryMethod) {
        String argumentName = name() + "Config";
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType)
                .javadoc(setterJavadoc(blueprintJavadoc)
                                 .addParameter(argumentName, blueprintJavadoc.returnDescription())
                                 .build())
                .update(it -> configured.annotations().forEach(it::addAnnotation))
                .addParameter(param -> param.name(argumentName)
                        .type(factoryMethod.argumentType()))
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
