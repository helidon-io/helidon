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
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.CHAR_ARRAY_TYPE;
import static io.helidon.builder.processor.Types.CONFIG_TYPE;
import static io.helidon.common.processor.GeneratorTools.capitalize;
import static io.helidon.common.types.TypeNames.OPTIONAL;

// declaration in builder is always non-generic, so no need to modify default values
class TypeHandlerOptional extends TypeHandler.OneTypeHandler {

    TypeHandlerOptional(String name, String getterName, String setterName, TypeName declaredType) {
        super(name, getterName, setterName, declaredType);
    }

    @Override
    String generateBuilderGetter() {
        return "Optional.ofNullable(" + name() + ")";
    }

    @Override
    String fieldDeclaration(PrototypeProperty.ConfiguredOption configured, boolean isBuilder, boolean alwaysFinal) {
        StringBuilder fieldDeclaration = new StringBuilder("private ");
        TypeName usedType = isBuilder ? actualType() : declaredType();

        if (alwaysFinal || !isBuilder) {
            fieldDeclaration.append("final ");
        }

        if (isBuilder && (configured.required() || !configured.hasDefault())) {
            // we need to use object types to be able to see if this was configured
            fieldDeclaration.append(usedType.boxed().fqName());
        } else {
            fieldDeclaration.append(usedType.fqName());
        }

        fieldDeclaration.append(" ")
                .append(name());

        if (isBuilder && configured.hasDefault()) {
            fieldDeclaration.append(" = ")
                    .append(configured.defaultValue());
        }

        return fieldDeclaration.toString();
    }

    @Override
    TypeName argumentTypeName() {
        return TypeName.builder(OPTIONAL)
                .addTypeArgument(toWildcard(actualType()));
    }

    @Override
    void setters(PrototypeProperty.ConfiguredOption configured,
                 PrototypeProperty.Singular singular,
                 List<GeneratedMethod> setters,
                 FactoryMethods factoryMethod,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        declaredSetter(setters, returnType, blueprintJavadoc);
        unsetSetter(setters, returnType, configured);

        // and add the setter with the actual type
        // config is special - handled directly when configuration is handled, as it also must be used when this type
        // is @Configured
        if (!actualType().equals(CONFIG_TYPE)) {
            // declared setter - optional is package local, field is never optional in builder
            List<String> lines = new ArrayList<>();
            lines.add("Objects.requireNonNull(" + name() + ");");
            lines.addAll(resolveBuilderLines(actualType(), name()));
            lines.add("this." + name() + " = " + name() + ";");
            lines.add("return me();");

            Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                          List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                          List.of("updated builder instance"),
                                          List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

            setters.add(new GeneratedMethod(
                    Set.of(setterModifier(configured).trim()),
                    setterName(),
                    returnType,
                    List.of(new GeneratedMethod.Argument(name(), actualType())),
                    List.of(),
                    javadoc,
                    lines
            ));
        }

        if (actualType().equals(CHAR_ARRAY_TYPE)) {
            charArraySetter(configured, setters, returnType, blueprintJavadoc);
        }

        if (factoryMethod.createTargetType().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            FactoryMethods.FactoryMethod fm = factoryMethod.createTargetType().get();
            String optionalSuffix = optionalSuffix(fm.factoryMethodReturnType());
            String argumentName = name() + "Config";

            Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                          List.of(new Javadoc.Tag(argumentName, blueprintJavadoc.returns())),
                                          List.of("updated builder instance"),
                                          List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

            List<String> lines = new ArrayList<>();
            lines.add("Objects.requireNonNull(" + argumentName + ");");
            lines.add("this." + name() + " = " + fm.typeWithFactoryMethod().genericTypeName().fqName() + "."
                              + fm.createMethodName() + "(" + argumentName + ")" + optionalSuffix + ";");
            lines.add("return me();");

            setters.add(new GeneratedMethod(
                    Set.of(setterModifier(configured).trim()),
                    setterName(),
                    returnType,
                    List.of(new GeneratedMethod.Argument(argumentName, fm.argumentType())),
                    List.of(),
                    javadoc,
                    lines
            ));
        }

        if (factoryMethod.builder().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            FactoryMethods.FactoryMethod fm = factoryMethod.builder().get();

            TypeName builderType;
            if (fm.factoryMethodReturnType().className().equals("Builder")) {
                builderType = fm.factoryMethodReturnType();
            } else {
                builderType = TypeName.create(fm.factoryMethodReturnType().fqName() + ".Builder");
            }

            String argumentName = "consumer";

            Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                          List.of(new Javadoc.Tag(argumentName, blueprintJavadoc.returns())),
                                          List.of("updated builder instance"),
                                          List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

            List<String> lines = new ArrayList<>();
            lines.add("Objects.requireNonNull(" + argumentName + ");");
            lines.add("var builder = " + fm.typeWithFactoryMethod().genericTypeName().fqName()
                              + "." + fm.createMethodName() + "();");
            lines.add("consumer.accept(builder);");
            lines.add("this." + name() + "(builder.build());");
            lines.add("return me();");

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
    }

    private void declaredSetter(List<GeneratedMethod> setters,
                                TypeName returnType,
                                Javadoc blueprintJavadoc) {
        // declared setter - optional is package local, field is never optional in builder
        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + name() + ");");
        lines.add("this." + name() + " = " + name() + ".orElse(null);");
        lines.add("return me();");

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        setters.add(new GeneratedMethod(
                Set.of(),
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(name(), argumentTypeName())),
                List.of(),
                javadoc,
                lines
        ));
    }

    private void unsetSetter(List<GeneratedMethod> setters,
                             TypeName returnType,
                             PrototypeProperty.ConfiguredOption configured) {
        // declared setter - optional is package local, field is never optional in builder
        List<String> lines = new ArrayList<>();
        lines.add("this." + name() + " = null;");
        lines.add("return me();");

        Javadoc javadoc = new Javadoc(List.of("Unset existing value of this property."),
                                      List.of(),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured)),
                "unset" + capitalize(name()),
                returnType,
                List.of(),
                List.of(),
                javadoc,
                lines
        ));
    }

    private String optionalSuffix(TypeName typeName) {
        if (OPTIONAL.equals(typeName.genericTypeName())) {
            return ".orElse(null)";
        }
        return "";
    }
}
