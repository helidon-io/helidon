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

import java.util.Map;
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

import static io.helidon.builder.processor.Types.CHAR_ARRAY_TYPE;
import static io.helidon.builder.processor.Types.CONFIG_TYPE;
import static io.helidon.common.processor.GeneratorTools.capitalize;
import static io.helidon.common.types.TypeNames.BOXED_BOOLEAN;
import static io.helidon.common.types.TypeNames.BOXED_BYTE;
import static io.helidon.common.types.TypeNames.BOXED_CHAR;
import static io.helidon.common.types.TypeNames.BOXED_DOUBLE;
import static io.helidon.common.types.TypeNames.BOXED_FLOAT;
import static io.helidon.common.types.TypeNames.BOXED_INT;
import static io.helidon.common.types.TypeNames.BOXED_LONG;
import static io.helidon.common.types.TypeNames.BOXED_SHORT;
import static io.helidon.common.types.TypeNames.BOXED_VOID;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.common.types.TypeNames.PRIMITIVE_BOOLEAN;
import static io.helidon.common.types.TypeNames.PRIMITIVE_BYTE;
import static io.helidon.common.types.TypeNames.PRIMITIVE_CHAR;
import static io.helidon.common.types.TypeNames.PRIMITIVE_DOUBLE;
import static io.helidon.common.types.TypeNames.PRIMITIVE_FLOAT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_INT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_LONG;
import static io.helidon.common.types.TypeNames.PRIMITIVE_SHORT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_VOID;

// declaration in builder is always non-generic, so no need to modify default values
class TypeHandlerOptional extends TypeHandler.OneTypeHandler {

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

    TypeHandlerOptional(String name, String getterName, String setterName, TypeName declaredType) {
        super(name, getterName, setterName, declaredType);
    }

    @Override
    Field.Builder fieldDeclaration(AnnotationDataOption configured, boolean isBuilder, boolean alwaysFinal) {
        Field.Builder builder = Field.builder()
                .isFinal(alwaysFinal || !isBuilder)
                .name(name());
        TypeName usedType = isBuilder ? actualType() : declaredType();

        if (isBuilder && (configured.required() || !configured.hasDefault())) {
            // we need to use object types to be able to see if this was configured
            builder.type(usedType.boxed());
        } else {
            builder.type(usedType);
        }

        if (isBuilder && configured.hasDefault()) {
            builder.defaultValue(configured.defaultValue());
        }

        return builder;
    }

    @Override
    TypeName argumentTypeName() {
        TypeName type = actualType();
        if (TypeNames.STRING.equals(type) || toPrimitive(type).primitive()) {
            return TypeName.builder(OPTIONAL)
                    .addTypeArgument(type)
                    .build();
        }

        return TypeName.builder(OPTIONAL)
                .addTypeArgument(toWildcard(actualType()))
                .build();
    }

    @Override
    void setters(InnerClass.Builder classBuilder,
                 AnnotationDataOption configured,
                 FactoryMethods factoryMethod,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        declaredSetter(classBuilder, returnType, blueprintJavadoc);
        clearSetter(classBuilder, returnType, configured);

        // and add the setter with the actual type
        // config is special - handled directly when configuration is handled, as it also must be used when this type
        // is @Configured
        if (!actualType().equals(CONFIG_TYPE)) {
            // declared setter - optional is package local, field is never optional in builder
            Method.Builder method = Method.builder()
                    .name(setterName())
                    .accessModifier(setterAccessModifier(configured))
                    .description(blueprintJavadoc.content())
                    .returnType(returnType, "updated builder instance")
                    .addParameter(param -> param.name(name())
                            .type(toPrimitive(actualType()))
                            .description(blueprintJavadoc.returnDescription()))
                    .addJavadocTag("see", "#" + getterName() + "()")
                    .typeName(Objects.class)
                    .addLine(".requireNonNull(" + name() + ");")
                    .addLine("this." + name() + " = " + name() + ";")
                    .addLine("return self();");
            classBuilder.addMethod(method);
        }

        if (actualType().equals(CHAR_ARRAY_TYPE)) {
            charArraySetter(classBuilder, configured, returnType, blueprintJavadoc);
        }

        if (factoryMethod.createTargetType().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            FactoryMethods.FactoryMethod fm = factoryMethod.createTargetType().get();
            String optionalSuffix = optionalSuffix(fm.factoryMethodReturnType());
            String argumentName = name() + "Config";

            classBuilder.addMethod(builder -> builder.name(setterName())
                    .accessModifier(setterAccessModifier(configured))
                    .description(blueprintJavadoc.content())
                    .returnType(returnType, "updated builder instance")
                    .addParameter(param -> param.name(argumentName)
                            .type(fm.argumentType())
                            .description(blueprintJavadoc.returnDescription()))
                    .addJavadocTag("see", "#" + getterName() + "()")
                    .typeName(Objects.class)
                    .addLine(".requireNonNull(" + argumentName + ");")
                    .add("this." + name() + " = ")
                    .typeName(fm.typeWithFactoryMethod().genericTypeName())
                    .addLine("." + fm.createMethodName() + "(" + argumentName + ")" + optionalSuffix + ";")
                    .addLine("return self();"));
        }

        if (factoryMethod.builder().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            FactoryMethods.FactoryMethod fm = factoryMethod.builder().get();

            TypeName builderType;
            String className = fm.factoryMethodReturnType().className();
            if (className.equals("Builder") || className.endsWith(".Builder")) {
                builderType = fm.factoryMethodReturnType();
            } else {
                builderType = TypeName.create(fm.factoryMethodReturnType().fqName() + ".Builder");
            }
            String argumentName = "consumer";
            TypeName argumentType = TypeName.builder()
                    .type(Consumer.class)
                    .addTypeArgument(builderType)
                    .build();

            Javadoc javadoc = setterJavadoc(blueprintJavadoc)
                            .addParameter(argumentName, blueprintJavadoc.returnDescription())
                            .build();

            classBuilder.addMethod(builder -> builder.name(setterName())
                    .accessModifier(setterAccessModifier(configured))
                    .returnType(returnType)
                    .addParameter(param -> param.name(argumentName)
                            .type(argumentType))
                    .typeName(Objects.class)
                    .javadoc(javadoc)
                    .addLine(".requireNonNull(" + argumentName + ");")
                    .add("var builder = ")
                    .typeName(fm.typeWithFactoryMethod().genericTypeName())
                    .addLine("." + fm.createMethodName() + "();")
                    .addLine("consumer.accept(builder);")
                    .addLine("this." + name() + "(builder.build());")
                    .addLine("return self();"));
        }
    }

    private void declaredSetter(InnerClass.Builder classBuilder,
                                TypeName returnType,
                                Javadoc blueprintJavadoc) {
        // declared setter - optional is package local, field is never optional in builder
        classBuilder.addMethod(builder -> builder.name(setterName())
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .description(blueprintJavadoc.content())
                .returnType(returnType, "updated builder instance")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))
                .addJavadocTag("see", "#" + getterName() + "()")
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + name() + ");")
                .addLine("this." + name() + " = " + name()
                                 + ".map(" + actualType().fqName() + ".class::cast)"
                                 + ".orElse(this." + name() + ");")
                .addLine("return self();"));
    }

    private void clearSetter(InnerClass.Builder classBuilder,
                             TypeName returnType,
                             AnnotationDataOption configured) {
        // declared setter - optional is package local, field is never optional in builder
        classBuilder.addMethod(builder -> builder.name("clear" + capitalize(name()))
                .accessModifier(setterAccessModifier(configured))
                .description("Clear existing value of this property.")
                .returnType(returnType, "updated builder instance")
                .addJavadocTag("see", "#" + getterName() + "()")
                .addLine("this." + name() + " = null;")
                .addLine("return self();"));
    }

    private String optionalSuffix(TypeName typeName) {
        if (OPTIONAL.equals(typeName.genericTypeName())) {
            return ".orElse(null)";
        }
        return "";
    }

    private TypeName toPrimitive(TypeName typeName) {
        return Optional.ofNullable(BOXED_TO_PRIMITIVE.get(typeName))
                .orElse(typeName);
    }
}
