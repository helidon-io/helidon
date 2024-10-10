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

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.CHAR_ARRAY;
import static io.helidon.codegen.CodegenUtil.capitalize;
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

    TypeHandlerOptional(TypeName blueprintType,
                        TypedElementInfo annotatedMethod,
                        String name, String getterName, String setterName, TypeName declaredType) {
        super(blueprintType, annotatedMethod, name, getterName, setterName, declaredType);
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
            configured.defaultValue().accept(builder);
        }

        return builder;
    }

    @Override
    TypeName argumentTypeName() {
        TypeName type = actualType();
        if (TypeNames.STRING.equals(type) || toPrimitive(type).primitive() || type.array()) {
            return declaredType();
        }

        return TypeName.builder(OPTIONAL)
                .addTypeArgument(toWildcard(type))
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
        if (!isConfigProperty(this)) {
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
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name() + ");")
                    .update(it -> {
                        if (configured.decorator() != null) {
                            it.addContent("new ")
                                    .addContent(configured.decorator())
                                    .addContent("().decorate(this, ")
                                    .addContent(Optional.class)
                                    .addContent(".of(")
                                    .addContent(name())
                                    .addContentLine("));");
                        }
                    })
                    .addContentLine("this." + name() + " = " + name() + ";")
                    .addContentLine("return self();");
            classBuilder.addMethod(method);
        }

        if (actualType().equals(CHAR_ARRAY)) {
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
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + argumentName + ");")
                    .addContent("this." + name() + " = ")
                    .addContent(fm.typeWithFactoryMethod().genericTypeName())
                    .addContentLine("." + fm.createMethodName() + "(" + argumentName + ")" + optionalSuffix + ";")
                    .addContentLine("return self();"));
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
                if (!fm.factoryMethodReturnType().typeArguments().isEmpty()) {
                    builderType = TypeName.builder(builderType)
                            .update(it -> fm.factoryMethodReturnType().typeArguments().forEach(it::addTypeArgument))
                            .build();
                }
            }
            String argumentName = "consumer";
            TypeName argumentType = TypeName.builder()
                    .type(Consumer.class)
                    .addTypeArgument(builderType)
                    .build();

            Javadoc javadoc = setterJavadoc(blueprintJavadoc)
                            .addParameter(argumentName, blueprintJavadoc.returnDescription())
                            .build();

            TypeName finalBuilderType = builderType;
            classBuilder.addMethod(builder -> builder.name(setterName())
                    .accessModifier(setterAccessModifier(configured))
                    .returnType(returnType)
                    .addParameter(param -> param.name(argumentName)
                            .type(argumentType))
                    .addContent(Objects.class)
                    .javadoc(javadoc)
                    .addContentLine(".requireNonNull(" + argumentName + ");")
                    .addContent("var builder = ")
                    .addContent(fm.typeWithFactoryMethod().genericTypeName())
                    .addContent(".")
                    .update(it -> {
                        if (!finalBuilderType.typeArguments().isEmpty()) {
                            it.addContent("<");
                            Iterator<TypeName> iterator = finalBuilderType.typeArguments().iterator();
                            while (iterator.hasNext()) {
                                it.addContent(iterator.next());
                                if (iterator.hasNext()) {
                                    it.addContent(", ");
                                }
                            }
                            it.addContent(">");
                        }
                    })
                    .addContentLine(fm.createMethodName() + "();")
                    .addContentLine("consumer.accept(builder);")
                    .addContentLine("this." + name() + "(builder.build());")
                    .addContentLine("return self();"));
        }
    }

    private void declaredSetter(InnerClass.Builder classBuilder,
                                TypeName returnType,
                                Javadoc blueprintJavadoc) {
        boolean generic = !actualType().typeArguments().isEmpty();
        // declared setter - optional is package local, field is never optional in builder
        classBuilder.addMethod(builder -> builder.name(setterName())
                .update(it -> {
                    if (generic) {
                        it.addAnnotation(Annotation.create(SuppressWarnings.class, "unchecked"));
                    }
                })
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .description(blueprintJavadoc.content())
                .returnType(returnType, "updated builder instance")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))
                .addJavadocTag("see", "#" + getterName() + "()")
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + name() + ");")
                .addContentLine("this." + name() + " = " + name()
                                 + ".map(" + actualType().fqName() + ".class::cast)"
                                 + ".orElse(this." + name() + ");")
                .addContentLine("return self();"));
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
                .update(it -> {
                    if (configured.decorator() != null) {
                        builder.addContent("new ")
                                .addContent(configured.decorator())
                                .addContent("().decorate(this, ")
                                .addContent(Optional.class)
                                .addContentLine(".empty());");
                    }
                })
                .addContentLine("this." + name() + " = null;")
                .addContentLine("return self();"));
    }

    private String optionalSuffix(TypeName typeName) {
        if (OPTIONAL.equals(typeName.genericTypeName())) {
            return ".orElse(null)";
        }
        return "";
    }
}
