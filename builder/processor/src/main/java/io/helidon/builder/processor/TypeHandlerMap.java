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

import io.helidon.common.processor.GeneratorTools;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.STRING_TYPE;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.OBJECT;
import static io.helidon.common.types.TypeNames.SET;

class TypeHandlerMap extends TypeHandler {
    private static final TypeName SAME_GENERIC_TYPE = TypeName.createFromGenericDeclaration("TYPE");
    private final TypeName actualType;
    private final String implTypeName;
    private final boolean sameGeneric;

    TypeHandlerMap(String name, String getterName, String setterName, TypeName declaredType, boolean sameGeneric) {
        super(name, getterName, setterName, declaredType);
        this.sameGeneric = sameGeneric;

        this.implTypeName = collectionImplType(MAP);
        if (declaredType.typeArguments().size() < 2) {
            this.actualType = STRING_TYPE;
        } else {
            this.actualType = declaredType.typeArguments().get(1);
        }
    }

    @Override
    String fieldDeclaration(PrototypeProperty.ConfiguredOption configured, boolean isBuilder, boolean alwaysFinal) {
        return super.fieldDeclaration(configured, isBuilder, true)
                + (isBuilder && !configured.hasDefault() ? " = new " + implTypeName + "<>()" : "");
    }

    @Override
    String toDefaultValue(String defaultValue) {
        // each two values form a key and a value
        String[] defaults = defaultValue.split(",");
        if (defaults.length % 2 != 0) {
            throw new IllegalArgumentException("Default value for a map does not have even number of entries:" + defaultValue);
        }

        for (int i = 1; i < defaults.length; i = i + 2) {
            defaults[i] = super.toDefaultValue(defaultValue);
        }

        return "java.util.Map.of(" + String.join(", ", defaults) + ")";
    }

    @Override
    TypeName actualType() {
        return actualType;
    }

    @Override
    Optional<String> generateFromConfig(PrototypeProperty.ConfiguredOption configured, FactoryMethods factoryMethods) {
        return Optional.of("config.get(\"" + configured.configKey() + "\").asNodeList().ifPresent(nodes -> nodes.forEach"
                                   + "(node -> "
                                   + name() + ".put(node.get(\"name\").asString().orElse(node.name()), node"
                                   + generateFromConfig(factoryMethods)
                                   + ".get())));");
    }

    @Override
    TypeName argumentTypeName() {
        return TypeName.builder(MAP)
                .addTypeArgument(toWildcard(declaredType().typeArguments().get(0)))
                .addTypeArgument(toWildcard(declaredType().typeArguments().get(1)))
                .build();
    }

    @SuppressWarnings("checkstyle:MethodLength") // will be shorter when we switch to class model
    @Override
    void setters(PrototypeProperty.ConfiguredOption configured,
                 PrototypeProperty.Singular singular,
                 List<GeneratedMethod> setters,
                 FactoryMethods factoryMethod,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        declaredSetter(configured, setters, returnType, blueprintJavadoc);
        declaredSetterAdd(configured, setters, returnType, blueprintJavadoc);

        if (factoryMethod.createTargetType().isPresent()) {
            // factory method
            FactoryMethods.FactoryMethod fm = factoryMethod.createTargetType().get();
            String argumentName = name() + "Config";

            List<String> lines = new ArrayList<>();
            lines.add("Objects.requireNonNull(" + argumentName + ");");
            lines.add("this." + name() + ".clear();");
            lines.add("this." + name() + ".putAll("
                              + fm.typeWithFactoryMethod().genericTypeName().fqName()
                              + "." + fm.createMethodName() + "(" + argumentName + "));");
            lines.add("return self();");

            List<String> docLines = new ArrayList<>(blueprintJavadoc.lines());
            docLines.add("This method keeps existing values, then puts all new values into the map.");

            Javadoc javadoc = new Javadoc(docLines,
                                          List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                          List.of("updated builder instance"),
                                          List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

            setters.add(new GeneratedMethod(
                    Set.of(setterModifier(configured).trim()),
                    null,
                    setterName(),
                    returnType,
                    List.of(new GeneratedMethod.Argument(argumentName, fm.argumentType())),
                    List.of(),
                    javadoc,
                    lines
            ));
        }

        TypeName keyType = declaredType().typeArguments().get(0);

        if (singular.hasSingular() && isCollection(actualType())) {
            // value is a collection as well, we need to generate `add` methods for adding a single value, and adding
            // collection values
            // builder.addValue(String key, String value)
            // builder.addValues(String key, Set<String> values)
            String singularName = singular.singularName();

            setters.add(setterAddValueToCollection(configured,
                                                   "add" + GeneratorTools.capitalize(singularName),
                                                   singularName,
                                                   keyType,
                                                   actualType().typeArguments().get(0),
                                                   returnType,
                                                   blueprintJavadoc));
            setters.add(setterAddValuesToCollection(configured,
                                                    "add" + GeneratorTools.capitalize(name()),
                                                    keyType,
                                                    returnType,
                                                    blueprintJavadoc));
        }
        if (singular.hasSingular()) {
            // Builder putValue(String key, String value)
            String singularName = singular.singularName();
            String methodName = "put" + GeneratorTools.capitalize(singularName);

            List<GeneratedMethod.Argument> args;
            String typeDeclaration = null;
            if (sameGeneric) {
                SameGenericArgs sameGenArgs = sameGenericArgs("key", keyType, singularName, actualType());
                typeDeclaration = sameGenArgs.typeDeclaration();
                args = sameGenArgs.arguments();
            } else {
                args = List.of(new GeneratedMethod.Argument("key", keyType),
                               new GeneratedMethod.Argument(singularName, actualType()));
            }

            List<Javadoc.Tag> docParamTags = new ArrayList<>();
            docParamTags.add(new Javadoc.Tag("key", List.of("key to add or replace")));
            docParamTags.add(new Javadoc.Tag(singularName, List.of("new value for the key")));
            if (sameGeneric) {
                docParamTags.add(new Javadoc.Tag("<TYPE>", List.of("Type to correctly map key and value")));
            }

            List<String> docLines = new ArrayList<>(blueprintJavadoc.lines());
            docLines.add("This method adds a new value to the map, or replaces it if the key already exists.");
            Javadoc javadoc = new Javadoc(docLines,
                                          docParamTags,
                                          List.of("updated builder instance"),
                                          List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

            List<String> lines = new ArrayList<>();
            lines.add("Objects.requireNonNull(key);");
            lines.add("Objects.requireNonNull(" + singularName + ");");
            lines.addAll(resolveBuilderLines(actualType(), singularName));
            lines.add("this." + name() + ".put(key, " + secondArgToPut(actualType(), singularName) + ");");
            lines.add("return self();");
            setters.add(new GeneratedMethod(
                    Set.of(setterModifier(configured).trim()),
                    typeDeclaration,
                    methodName,
                    returnType,
                    args,
                    List.of(),
                    javadoc,
                    lines));

            if (factoryMethod.builder().isPresent()) {
                FactoryMethods.FactoryMethod fm = factoryMethod.builder().get();
                TypeName builderType;
                if (fm.factoryMethodReturnType().className().equals("Builder")) {
                    builderType = fm.factoryMethodReturnType();
                } else {
                    builderType = TypeName.create(fm.factoryMethodReturnType().fqName() + ".Builder");
                }

                GeneratedMethod.Argument keyArg = new GeneratedMethod.Argument("key", keyType);
                GeneratedMethod.Argument valArg = new GeneratedMethod.Argument("consumer", TypeName.builder()
                        .type(Consumer.class)
                        .addTypeArgument(builderType)
                        .build());

                docLines = new ArrayList<>(blueprintJavadoc.lines());
                docLines.add("This method adds a new value to the map, or replaces it if the key already exists.");
                javadoc = new Javadoc(docLines,
                                      List.of(new Javadoc.Tag("key", List.of("key to add or replace")),
                                              new Javadoc.Tag("consumer",
                                                              List.of("builder consumer to create new value for the key"))),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

                lines = new ArrayList<>();
                lines.add("Objects.requireNonNull(key);");
                lines.add("Objects.requireNonNull(consumer);");
                lines.add("var builder = " + fm.typeWithFactoryMethod().genericTypeName().fqName()
                                  + "." + fm.createMethodName() + "();");
                lines.add("consumer.accept(builder);");
                lines.add("this." + methodName + "(key, builder.build());");
                lines.add("return self();");

                setters.add(new GeneratedMethod(
                        Set.of(setterModifier(configured).trim()),
                        null,
                        methodName,
                        returnType,
                        List.of(keyArg, valArg),
                        List.of(),
                        javadoc,
                        lines));
            }
        }
    }

    private SameGenericArgs sameGenericArgs(String key, TypeName keyType, String value, TypeName valueType) {

        String typeDeclaration;
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
            TypeName typeArg = keyType.typeArguments().get(0);
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
            throw new IllegalArgumentException("Property " + name() + " with type " + declaredType().fqName() + " is annotated"
                                                       + " with @SameGeneric, yet the key generic type cannot be determined."
                                                       + " Either the key must be a simple type, or a type with one type"
                                                       + " argument.");
        }

        typeDeclaration = "<TYPE extends " + genericTypeBase.fqName() + ">";

        // now resolve value
        if (valueType.typeArguments().isEmpty()) {
            if (!genericTypeBase.equals(valueType)) {
                throw new IllegalArgumentException("Property " + name() + " with type " + declaredType().fqName() + " is "
                                                           + "annotated"
                                                           + " with @SameGeneric, yet the type of value is not the"
                                                           + " same as type found on key: " + genericTypeBase.fqName());
            }
            resolvedValueType = SAME_GENERIC_TYPE;
        } else if (valueType.typeArguments().size() == 1) {
            if (!genericTypeBase.equals(valueType.typeArguments().get(0))) {
                throw new IllegalArgumentException("Property " + name() + " with type " + declaredType().fqName() + " is "
                                                           + "annotated"
                                                           + " with @SameGeneric, yet type of value is not the"
                                                           + " same as type found on key: " + genericTypeBase.fqName());
            }
            resolvedValueType = TypeName.builder(valueType)
                    .typeArguments(List.of(SAME_GENERIC_TYPE))
                    .build();
        } else {
            throw new IllegalArgumentException("Property " + name() + " with type " + declaredType().fqName() + " is annotated"
                                                       + " with @SameGeneric, yet the value generic type cannot be determined."
                                                       + " Either the value must be a simple type, or a type with one type"
                                                       + " argument.");
        }

        return new SameGenericArgs(typeDeclaration, List.of(new GeneratedMethod.Argument(key, resolvedKeyType),
                                                            new GeneratedMethod.Argument(value, resolvedValueType)));
    }

    private GeneratedMethod setterAddValueToCollection(PrototypeProperty.ConfiguredOption configured,
                                                       String methodName,
                                                       String singularName,
                                                       TypeName keyType,
                                                       TypeName valueType,
                                                       TypeName returnType,
                                                       Javadoc blueprintJavadoc) {

        GeneratedMethod.Argument keyArg = new GeneratedMethod.Argument("key", keyType);
        GeneratedMethod.Argument valArg = new GeneratedMethod.Argument(singularName, valueType);
        String implType = collectionImplType(actualType());
        List<String> methodLines = List.of(
                "Objects.requireNonNull(key);",
                "Objects.requireNonNull(" + singularName + ");",
                "this." + name() + ".compute(key, (k, v) -> {",
                "    v = v == null ? new " + implType + "<>() : new " + implType + "<>(v);",
                "    v.add(" + singularName + ");",
                "    return v;",
                "});",
                "return self();"
        );

        List<String> docLines = new ArrayList<>(blueprintJavadoc.lines());
        docLines.add("This method adds a new value to the map value, or creates a new value.");
        Javadoc javadoc = new Javadoc(docLines,
                                      List.of(new Javadoc.Tag("key", List.of("key to add to")),
                                              new Javadoc.Tag(singularName, List.of("additional value for the key"))),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        return new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                null,
                methodName,
                returnType,
                List.of(keyArg, valArg),
                List.of(),
                javadoc,
                methodLines
        );
    }

    private GeneratedMethod setterAddValuesToCollection(PrototypeProperty.ConfiguredOption configured,
                                                        String methodName,
                                                        TypeName keyType,
                                                        TypeName returnType,
                                                        Javadoc blueprintJavadoc) {

        GeneratedMethod.Argument keyArg = new GeneratedMethod.Argument("key", keyType);
        GeneratedMethod.Argument valArg = new GeneratedMethod.Argument(name(), actualType());
        String implType = collectionImplType(actualType());
        List<String> methodLines = List.of(
                "Objects.requireNonNull(key);",
                "Objects.requireNonNull(" + name() + ");",
                "this." + name() + ".compute(key, (k, v) -> {",
                "    v = v == null ? new " + implType + "<>() : new " + implType + "<>(v);",
                "    v.addAll(" + name() + ");",
                "    return v;",
                "});",
                "return self();"
        );

        List<String> docLines = new ArrayList<>(blueprintJavadoc.lines());
        docLines.add("This method adds a new value to the map value, or creates a new value.");
        Javadoc javadoc = new Javadoc(docLines,
                                      List.of(new Javadoc.Tag("key", List.of("key to add to")),
                                              new Javadoc.Tag(name(), List.of("additional values for the key"))),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        return new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                null,
                methodName,
                returnType,
                List.of(keyArg, valArg),
                List.of(),
                javadoc,
                methodLines
        );
    }

    private void declaredSetterAdd(PrototypeProperty.ConfiguredOption configured,
                                   List<GeneratedMethod> setters,
                                   TypeName returnType,
                                   Javadoc blueprintJavadoc) {
        // declared type - add content
        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + name() + ");");
        lines.add("this." + name() + ".putAll(" + name() + ");");
        lines.add("return self();");

        List<String> docLines = new ArrayList<>(blueprintJavadoc.lines());
        docLines.add("This method keeps existing values, then puts all new values into the map.");

        Javadoc javadoc = new Javadoc(docLines,
                                      List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                null,
                "add" + GeneratorTools.capitalize(name()),
                returnType,
                List.of(new GeneratedMethod.Argument(name(), argumentTypeName())),
                List.of(),
                javadoc,
                lines
        ));
    }

    private void declaredSetter(PrototypeProperty.ConfiguredOption configured,
                                List<GeneratedMethod> setters,
                                TypeName returnType,
                                Javadoc blueprintJavadoc) {
        // declared type (such as Map<String, String>) - replace content
        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + name() + ");");
        lines.add("this." + name() + ".clear();");
        lines.add("this." + name() + ".putAll(" + name() + ");");
        lines.add("return self();");

        List<String> docLines = new ArrayList<>(blueprintJavadoc.lines());
        docLines.add("This method replaces all values with the new ones.");

        Javadoc javadoc = new Javadoc(docLines,
                                      List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        // there is always a setter with the declared type
        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                null,
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(name(), argumentTypeName())),
                List.of(),
                javadoc,
                lines
        ));
    }

    private String secondArgToPut(TypeName typeName, String singularName) {
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(LIST)) {
            return "java.util.List.copyOf(" + singularName + ")";
        } else if (genericTypeName.equals(SET)) {
            return "java.util.Set.copyOf(" + singularName + ")";
        } else if (genericTypeName.equals(MAP)) {
            return "java.util.Map.copyOf(" + singularName + ")";
        }
        return singularName;
    }

    private boolean isCollection(TypeName typeName) {
        if (typeName.typeArguments().size() != 1) {
            return false;
        }
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(LIST)) {
            return true;
        }
        if (genericTypeName.equals(SET)) {
            return true;
        }
        return false;
    }

    record SameGenericArgs(String typeDeclaration, List<GeneratedMethod.Argument> arguments) {
    }
}
