/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenValidator;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.Size;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.OPTION_DEFAULT;
import static io.helidon.builder.codegen.Types.SERVICES;
import static io.helidon.common.types.TypeNames.BOXED_BOOLEAN;
import static io.helidon.common.types.TypeNames.BOXED_BYTE;
import static io.helidon.common.types.TypeNames.BOXED_CHAR;
import static io.helidon.common.types.TypeNames.BOXED_DOUBLE;
import static io.helidon.common.types.TypeNames.BOXED_FLOAT;
import static io.helidon.common.types.TypeNames.BOXED_INT;
import static io.helidon.common.types.TypeNames.BOXED_LONG;
import static io.helidon.common.types.TypeNames.BOXED_SHORT;
import static io.helidon.common.types.TypeNames.BOXED_VOID;
import static io.helidon.common.types.TypeNames.PRIMITIVE_BOOLEAN;
import static io.helidon.common.types.TypeNames.PRIMITIVE_BYTE;
import static io.helidon.common.types.TypeNames.PRIMITIVE_CHAR;
import static io.helidon.common.types.TypeNames.PRIMITIVE_DOUBLE;
import static io.helidon.common.types.TypeNames.PRIMITIVE_FLOAT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_INT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_LONG;
import static io.helidon.common.types.TypeNames.PRIMITIVE_SHORT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_VOID;

class TypeHandler {
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

    private final TypeName enclosingType;
    private final TypedElementInfo annotatedMethod;
    private final String name;
    private final String getterName;
    private final String setterName;
    private final TypeName declaredType;

    TypeHandler(TypeName enclosingType,
                TypedElementInfo annotatedMethod,
                String name,
                String getterName,
                String setterName,
                TypeName declaredType) {
        this.enclosingType = enclosingType;
        this.annotatedMethod = annotatedMethod;
        this.name = name;
        this.getterName = getterName;
        this.setterName = setterName;
        this.declaredType = declaredType;
    }

    static TypeHandler create(TypeName blueprintType,
                              TypedElementInfo annotatedMethod,
                              String name,
                              String getterName,
                              String setterName,
                              TypeName returnType,
                              boolean sameGeneric) {
        if (TypeNames.OPTIONAL.equals(returnType)) {
            return new TypeHandlerOptional(blueprintType, annotatedMethod, name, getterName, setterName, returnType);
        }
        if (TypeNames.SUPPLIER.equals(returnType)) {
            return new TypeHandlerSupplier(blueprintType, annotatedMethod, name, getterName, setterName, returnType);
        }

        if (TypeNames.SET.equals(returnType)) {
            checkTypeArgsSizeAndTypes(annotatedMethod, returnType, TypeNames.SET, 1);
            return new TypeHandlerSet(blueprintType, annotatedMethod, name, getterName, setterName, returnType);
        }

        if (TypeNames.LIST.equals(returnType)) {
            checkTypeArgsSizeAndTypes(annotatedMethod, returnType, TypeNames.LIST, 1);
            return new TypeHandlerList(blueprintType, annotatedMethod, name, getterName, setterName, returnType);
        }
        if (TypeNames.MAP.equals(returnType)) {
            checkTypeArgsSizeAndTypes(annotatedMethod, returnType, TypeNames.MAP, 2);
            return new TypeHandlerMap(blueprintType, annotatedMethod, name, getterName, setterName, returnType, sameGeneric);
        }

        return new TypeHandler(blueprintType, annotatedMethod, name, getterName, setterName, returnType);
    }

    static AccessModifier setterAccessModifier(AnnotationDataOption configured) {
        return configured.accessModifier();
    }

    static TypeName toWildcard(TypeName typeName) {
        if (typeName.wildcard()) {
            return typeName;
        }
        if (typeName.generic()) {
            return TypeName.builder()
                    .className(typeName.className())
                    .wildcard(true)
                    .build();
        }
        return TypeName.builder(typeName).wildcard(true).build();
    }

    static boolean isConfigProperty(TypeHandler handler) {
        return "config".equals(handler.name())
                && handler.actualType().equals(Types.COMMON_CONFIG);
    }

    protected static TypeName collectionImplType(TypeName typeName) {
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(TypeNames.MAP)) {
            return Types.LINKED_HASH_MAP;
        }
        if (genericTypeName.equals(TypeNames.LIST)) {
            return Types.ARRAY_LIST;
        }

        return Types.LINKED_HASH_SET;
    }

    @Override
    public String toString() {
        return declaredType.fqName() + " " + name;
    }

    void updateBuilderFromServices(ContentBuilder<?> content, String builder) {
        /*
        Services.first(Type.class).ifPresent(builder::option);
         */
        content.addContent(SERVICES)
                .addContent(".first(")
                .addContent(actualType())
                .addContent(".class).ifPresent(")
                .addContent(builder)
                .addContent("::")
                .addContent(setterName())
                .addContentLine(");");
    }

    void updateBuilderFromRegistry(ContentBuilder<?> content, String builder, String registry) {
        /*
        registry.first(Type.class).ifPresent(builder::option);
         */
        content.addContent(registry)
                .addContent(".first(")
                .addContent(actualType())
                .addContent(".class).ifPresent(")
                .addContent(builder)
                .addContent("::")
                .addContent(setterName())
                .addContentLine(");");
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

    void generateBuilderGetter(ContentBuilder<?> contentBuilder,
                               boolean required,
                               boolean hasDefault) {
        contentBuilder.addContent("return ");
        if (builderGetterOptional(required, hasDefault)) {
            contentBuilder.addContent(Optional.class)
                    .addContent(".ofNullable(")
                    .addContent(name)
                    .addContent(")");
        } else {
            contentBuilder.addContent(name);
        }
        contentBuilder.addContentLine(";");
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
            configured.defaultValue().accept(builder);
        }

        return builder;
    }

    Consumer<ContentBuilder<?>> toDefaultValue(String defaultValue) {
        TypeName typeName = actualType();
        if (TypeNames.STRING.equals(typeName)) {
            return content -> content.addContent("\"")
                    .addContent(defaultValue)
                    .addContent("\"");
        }
        if (TypeNames.SIZE.equals(typeName)) {
            CodegenValidator.validateSize(enclosingType, annotatedMethod, OPTION_DEFAULT, "value", defaultValue);
            return content -> content.addContent(Size.class)
                    .addContent(".parse(\"")
                    .addContent(defaultValue)
                    .addContent("\")");
        }
        if (TypeNames.DURATION.equals(typeName)) {
            CodegenValidator.validateDuration(enclosingType, annotatedMethod, OPTION_DEFAULT, "value", defaultValue);
            return content -> content.addContent(Duration.class)
                    .addContent(".parse(\"")
                    .addContent(defaultValue)
                    .addContent("\")");
        }
        if (Types.CHAR_ARRAY.equals(typeName)) {
            return content -> content.addContent("\"")
                    .addContent(defaultValue)
                    .addContent("\".toCharArray()");
        }
        if (Types.PATH.equals(typeName)) {
            return content -> content.addContent(Paths.class)
                    .addContent(".get(\"")
                    .addContent(defaultValue)
                    .addContent("\")");
        }
        if (Types.URI.equals(typeName)) {
            CodegenValidator.validateUri(enclosingType, annotatedMethod, OPTION_DEFAULT, "value", defaultValue);
            return content -> content.addContent(URI.class)
                    .addContent(".create(\"")
                    .addContent(defaultValue)
                    .addContent("\")");
        }
        if (typeName.primitive()) {
            if (typeName.fqName().equals("char")) {
                return content -> content.addContent("'")
                        .addContent(defaultValue)
                        .addContent("'");
            }
            return content -> content.addContent(defaultValue);
        }
        if (typeName.name().startsWith("java.")) {
            return content -> content.addContent(defaultValue);
        }
        // should be an enum
        return content -> content.addContent(typeName.genericTypeName())
                .addContent(".")
                .addContent(defaultValue);
    }

    Consumer<ContentBuilder<?>> toDefaultValue(List<String> defaultValues,
                                               List<Integer> defaultInts,
                                               List<Long> defaultLongs,
                                               List<Double> defaultDoubles,
                                               List<Boolean> defaultBooleans,
                                               String defaultCode,
                                               AnnotationDataOption.DefaultMethod defaultMethod) {
        if (defaultCode != null) {
            return content -> content.addContent(defaultCode);
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

    Consumer<ContentBuilder<?>> toDefaultValue(List<String> defaultValues,
                                               List<Integer> defaultInts,
                                               List<Long> defaultLongs,
                                               List<Double> defaultDoubles,
                                               List<Boolean> defaultBooleans) {
        if (defaultValues != null) {
            String string = singleDefault(defaultValues);
            return toDefaultValue(string);
        }
        if (defaultInts != null) {
            return content -> content.addContent(String.valueOf(singleDefault(defaultInts)));
        }
        if (defaultLongs != null) {
            return content -> content.addContent(singleDefault(defaultLongs) + "L");
        }
        if (defaultDoubles != null) {
            return content -> content.addContent(String.valueOf(singleDefault(defaultDoubles)));
        }
        if (defaultBooleans != null) {
            return content -> content.addContent(String.valueOf(singleDefault(defaultBooleans)));
        }

        return contentBuilder -> {
        };
    }

    protected Consumer<ContentBuilder<?>> toDefaultFromMethod(AnnotationDataOption.DefaultMethod defaultMethod) {
        return content -> content.addContent(defaultMethod.type().genericTypeName())
                .addContent(".")
                .addContent(defaultMethod.method())
                .addContent("()");
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
        method.addContent(configGet(configured));
        String fqName = actualType().fqName();

        if (fqName.endsWith(".Builder")) {
            // this is a special case - we have a builder field
            if (configured.hasDefault()) {
                method.addContent(".as(")
                        .addContent(Types.COMMON_CONFIG)
                        .addContent(".class).ifPresent(")
                        .addContent(name())
                        .addContentLine("::config);");
            } else {
                // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
                int lastDot = fqName.lastIndexOf('.');
                String builderMethod = fqName.substring(0, lastDot) + ".builder()";
                method.addContentLine(".map(" + builderMethod + "::config).ifPresent(this::" + setterName() + ");");
            }
        } else {
            generateFromConfig(method, factoryMethods);
            method.addContentLine(".ifPresent(this::" + setterName() + ");");
        }
    }

    String configGet(AnnotationDataOption configured) {
        if (configured.configMerge()) {
            return "config";
        }
        return "config.get(\"" + configured.configKey() + "\")";
    }

    void generateFromConfig(ContentBuilder<?> content, FactoryMethods factoryMethods) {
        if (actualType().fqName().equals("char[]")) {
            content.addContent(".asString().as(")
                    .addContent(String.class)
                    .addContent("::toCharArray)");
            return;
        }

        Optional<FactoryMethods.FactoryMethod> factoryMethod = factoryMethods.createFromConfig();

        TypeName boxed = actualType().boxed();
        if (factoryMethod.isPresent()) {
            FactoryMethods.FactoryMethod fm = factoryMethod.get();
            content.addContent(".map(")
                    .addContent(fm.typeWithFactoryMethod().genericTypeName())
                    .addContent("::");
            if (!actualType().typeArguments().isEmpty()) {
                content.addContent("<");
                Iterator<TypeName> iterator = actualType().typeArguments().iterator();
                while (iterator.hasNext()) {
                    content.addContent(iterator.next());
                    if (iterator.hasNext()) {
                        content.addContent(", ");
                    }
                }
                content.addContent(">");
            }
            content.addContent(fm.createMethodName())
                    .addContent(")");
        } else {
            content.addContent(".as(")
                    .addContent(boxed.genericTypeName())
                    .addContent(".class)");
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

        if (actualType().equals(Types.CHAR_ARRAY)) {
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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + argumentName + ");")
                .addContent("var builder = ");

        if (configured.hasDefault()) {
            builder.addContentLine("this." + name() + ";");
        } else {
            String fqName = actualType().fqName();
            // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
            int lastDot = fqName.lastIndexOf('.');
            String builderMethod = fqName.substring(0, lastDot) + ".builder()";
            builder.addContentLine(builderMethod + ";");
        }

        builder.addContentLine("consumer.accept(builder);")
                .addContentLine("this." + name() + "(builder);")
                .addContentLine("return self();");
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
                .addParameter(param -> param.name(name())
                        .type(TypeNames.STRING))
                .javadoc(setterJavadoc(blueprintJavadoc)
                                 .addParameter(name(), blueprintJavadoc.returnDescription())
                                 .build())
                .accessModifier(setterAccessModifier(configured))
                .update(it -> configured.annotations().forEach(it::addAnnotation))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + name() + ");")
                .addContentLine("this." + name() + " = " + name() + ".toCharArray();")
                .addContentLine("return self();"));
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
            builder.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name() + ");");
        }

        if (configured.decorator() != null) {
            builder.addContent("new ")
                    .addContent(configured.decorator())
                    .addContent("().decorate(this, ")
                    .addContent(name())
                    .addContentLine(");");
        }

        builder.addContentLine("this." + name() + " = " + name() + ";");

        builder.addContentLine("return self();");
        classBuilder.addMethod(builder);
    }

    protected TypeName toPrimitive(TypeName typeName) {
        return Optional.ofNullable(BOXED_TO_PRIMITIVE.get(typeName))
                .orElse(typeName);
    }

    private static void checkTypeArgsSizeAndTypes(TypedElementInfo annotatedMethod,
                                                  TypeName returnType,
                                                  TypeName collectionType,
                                                  int expectedTypeArgs) {
        List<TypeName> typeNames = returnType.typeArguments();
        if (typeNames.size() != expectedTypeArgs) {
            throw new CodegenException("Property of type " + collectionType.fqName() + " must have " + expectedTypeArgs
                                               + " type arguments defined",
                                       annotatedMethod.originatingElementValue());
        }
        for (TypeName typeName : typeNames) {
            if (typeName.wildcard()) {
                throw new CodegenException("Property of type " + returnType.resolvedName() + " is not supported for builder,"
                                                   + " as wildcards cannot be handled correctly in setters",
                                           annotatedMethod.originatingElementValue());
            }
        }
    }

    private <T> T singleDefault(List<T> defaultValues) {
        if (defaultValues.isEmpty()) {
            throw new IllegalArgumentException("Default values configured for " + name() + " are empty, one value is expected.");
        }
        if (defaultValues.size() > 1) {
            throw new IllegalArgumentException("Default values configured for " + name() + " contain more than one value,"
                                                       + " exactly one value is expected.");
        }
        return defaultValues.getFirst();
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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + argumentName + ");")
                .addContent("var builder = ")
                .addContent(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addContentLine("." + factoryMethod.createMethodName() + "();")
                .addContentLine("consumer.accept(builder);")
                .addContentLine("this." + name() + "(builder.build());")
                .addContentLine("return self();");
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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + argumentName + ");")
                .addContentLine("this." + name() + "(" + argumentName + ".get());")
                .addContentLine("return self();");
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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + argumentName + ");")
                .addContent("this." + name() + " = ")
                .addContent(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addContentLine("." + factoryMethod.createMethodName() + "(" + argumentName + ");")
                .addContentLine("return self();");
        classBuilder.addMethod(builder);
    }

    static class OneTypeHandler extends TypeHandler {
        private final TypeName actualType;

        OneTypeHandler(TypeName enclosingType,
                       TypedElementInfo annotatedMethod,
                       String name,
                       String getterName,
                       String setterName,
                       TypeName declaredType) {
            super(enclosingType, annotatedMethod, name, getterName, setterName, declaredType);

            if (declaredType.typeArguments().isEmpty()) {
                this.actualType = TypeNames.STRING;
            } else {
                this.actualType = declaredType.typeArguments().getFirst();
            }
        }

        @Override
        TypeName actualType() {
            return actualType;
        }
    }
}
