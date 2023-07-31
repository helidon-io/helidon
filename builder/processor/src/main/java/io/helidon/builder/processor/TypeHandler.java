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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

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

    static String setterModifier(PrototypeProperty.ConfiguredOption configured) {
        if (configured.builderMethod()) {
            return "public ";
        }
        return "";
    }

    static TypeName toWildcard(TypeName typeName) {
        if (typeName.wildcard()) {
            return typeName;
        }
        return TypeName.builder(typeName).wildcard(true).build();
    }

    protected static String collectionImplType(TypeName typeName) {
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(TypeNames.MAP)) {
            return LINKED_HASH_MAP_TYPE.fqName();
        }
        if (genericTypeName.equals(TypeNames.LIST)) {
            return ARRAY_LIST_TYPE.fqName();
        }

        return LINKED_HASH_SET_TYPE.fqName();
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
            return "java.util.Optional.ofNullable(" + name + ")";
        } else {
            return name;
        }
    }

    String fieldDeclaration(PrototypeProperty.ConfiguredOption configured,
                            boolean isBuilder,
                            boolean alwaysFinal) {
        StringBuilder fieldDeclaration = new StringBuilder("private ");

        if (alwaysFinal || !isBuilder) {
            fieldDeclaration.append("final ");
        }

        if (isBuilder && (configured.required())) {
            // we need to use object types to be able to see if this was configured
            fieldDeclaration.append(declaredType.boxed().fqName());
        } else {
            fieldDeclaration.append(declaredType.fqName());
        }

        fieldDeclaration.append(" ")
                .append(name);

        if (isBuilder && configured.hasDefault()) {
            fieldDeclaration.append(" = ")
                    .append(configured.defaultValue());
        }

        return fieldDeclaration.toString();
    }

    String toDefaultValue(String defaultValue) {
        TypeName typeName = actualType();
        if (STRING_TYPE.equals(typeName)) {
            return "\"" + defaultValue + "\"";
        }
        if (DURATION_TYPE.equals(typeName)) {
            return "java.time.Duration.parse(\"" + defaultValue + "\")";
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
        return typeName.genericTypeName().fqName() + "." + defaultValue;
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

    Optional<String> generateFromConfig(PrototypeProperty.ConfiguredOption configured, FactoryMethods factoryMethods) {
        return Optional.of(configGet(configured)
                                   + generateFromConfig(factoryMethods)
                                   + ".ifPresent(this::" + setterName() + ");");
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

    TypeName argumentTypeName() {
        return declaredType();
    }

    void setters(PrototypeProperty.ConfiguredOption configured,
                 PrototypeProperty.Singular singular,
                 List<GeneratedMethod> setters,
                 FactoryMethods factoryMethod,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        declaredSetter(configured, setters, returnType, blueprintJavadoc);

        if (actualType().equals(CHAR_ARRAY_TYPE)) {
            charArraySetter(configured, setters, returnType, blueprintJavadoc);
        }

        // if there is a factory method for the return type, we also have setters for the type (probably config object)
        if (factoryMethod.createTargetType().isPresent()) {
            factorySetter(configured, setters, returnType, blueprintJavadoc, factoryMethod.createTargetType().get());
        }

        // if there is a builder factory method, we create a method with builder consumer
        if (factoryMethod.builder().isPresent()) {
            factorySetterConsumer(configured, setters, returnType, blueprintJavadoc, factoryMethod.builder().get());
        }
    }

    protected void charArraySetter(PrototypeProperty.ConfiguredOption configured,
                                   List<GeneratedMethod> setters,
                                   TypeName returnType,
                                   Javadoc blueprintJavadoc) {
        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + name() + ");");
        lines.add("this." + name() + " = " + name() + ".toCharArray();");
        lines.add("return self();");

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        // there is always a setter with the declared type
        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(name(), STRING_TYPE)),
                List.of(),
                javadoc,
                lines
        ));
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

    private void declaredSetter(PrototypeProperty.ConfiguredOption configured,
                                List<GeneratedMethod> setters,
                                TypeName returnType,
                                Javadoc blueprintJavadoc) {
        List<String> lines = new ArrayList<>();
        if (!declaredType.primitive()) {
            lines.add("Objects.requireNonNull(" + name() + ");");
        }
        lines.add("this." + name() + " = " + name() + ";");
        lines.add("return self();");

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        // there is always a setter with the declared type
        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(name(), argumentTypeName())),
                List.of(),
                javadoc,
                lines
        ));
    }

    private void factorySetterConsumer(PrototypeProperty.ConfiguredOption configured,
                                       List<GeneratedMethod> setters,
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
        paramLines.addAll(blueprintJavadoc.returns());
        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(argumentName, paramLines)),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + argumentName + ");");
        lines.add("var builder = "
                          + factoryMethod.typeWithFactoryMethod().genericTypeName().fqName()
                          + "." + factoryMethod.createMethodName() + "();");
        lines.add("consumer.accept(builder);");
        lines.add("this." + name() + "(builder.build());");
        lines.add("return self();");

        TypeName argumentType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(builderType)
                .build();

        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(argumentName, argumentType)),
                List.of(),
                javadoc,
                lines
        ));
    }

    private void factorySetter(PrototypeProperty.ConfiguredOption configured,
                               List<GeneratedMethod> setters,
                               TypeName returnType,
                               Javadoc blueprintJavadoc,
                               FactoryMethods.FactoryMethod factoryMethod) {
        String argumentName = name() + "Config";

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(argumentName, blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + argumentName + ");");
        lines.add("this." + name() + " = " + factoryMethod.typeWithFactoryMethod().genericTypeName().fqName()
                          + "." + factoryMethod.createMethodName() + "(" + argumentName + ");");
        lines.add("return self();");
        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(argumentName, factoryMethod.argumentType())),
                List.of(),
                javadoc,
                lines
        ));
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
