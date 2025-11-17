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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.CHAR_ARRAY;

class TypeHandlerSupplier extends TypeHandlerBasic {

    TypeHandlerSupplier(List<BuilderCodegenExtension> extensions, PrototypeInfo prototypeInfo, OptionInfo option) {
        super(extensions, prototypeInfo, option, firstTypeArgument(option));
    }

    @Override
    public void fields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder) {
        Field.Builder builder = Field.builder()
                .type(option().declaredType())
                .name(option().name())
                .isFinal(!isBuilder);

        if (isBuilder && option().defaultValue().isPresent()) {
            builder.addContent("() -> ");
            option().defaultValue().get().accept(builder);
        }

        classBuilder.addField(builder);
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        if (option().provider().isPresent()) {
            return;
        }
        Optional<FactoryMethod> factoryMethod = optionConfigured.factoryMethod();
        String setterName = option().setterName();

        if (factoryMethod.isPresent()) {
            method.addContent(configGet(optionConfigured));
            generateFromConfig(method, factoryMethod.get());
            method.addContentLine(".ifPresent(this::" + setterName + ");");
        } else if (type().isOptional()) {
            method.addContent(setterName + "(");
            method.addContent(configGet(optionConfigured));
            method.addContent(generateFromConfigOptional());
            method.addContentLine(".optionalSupplier());");
        } else {
            method.addContent(setterName + "(");
            method.addContent(configGet(optionConfigured));
            generateFromConfig(method);
            method.addContentLine(".supplier());");
        }
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSetterDeclared(Javadoc getterJavadoc) {
        TypeName typeName = option().declaredType();
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();
        boolean generic = !type().typeArguments().isEmpty();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .typeName(returnType)
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        if (generic) {
            method.addAnnotation(Annotation.create(SuppressWarnings.class, "unchecked"));
        }

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(typeName)
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            if (!typeName.primitive()) {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(" + name + ");");
            }

            it.addContentLine("this." + name + " = " + name + "::get;")
                    .addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .javadoc(setterJavadoc(getterJavadoc, name, ""))
                                           .contentBuilder(contentConsumer)
                                           .build());
    }

    TypeName builderGetterType() {
        return option().declaredType();
    }

    @Override
    GeneratedMethod prepareBuilderGetter(Javadoc javadoc) {
        return super.prepareBuilderGetter(javadoc);
    }

    @Override
    GeneratedMethod prepareBuilderSetter(Javadoc getterJavadoc) {
        TypeName typeName = type();
        if (typeName.equals(CHAR_ARRAY)) {
            return stringSetterForCharArrayBuilderSetter(getterJavadoc);
        }
        return realDeclaredBuilderSetter(getterJavadoc);
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSetterCharArray(Javadoc getterJavadoc) {

        if (type().equals(CHAR_ARRAY)) {
            return Optional.of(realDeclaredBuilderSetter(getterJavadoc));
        }
        return Optional.empty();
    }

    @Override
    GeneratedMethod realDeclaredBuilderSetter(Javadoc getterJavadoc) {
        TypeName typeName = type();
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(typeName)
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            if (!typeName.primitive()) {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(" + name + ");");
            }

            it.addContentLine("this." + name + " = () -> " + name + ";")
                    .addContentLine("return self();");
        };

        return GeneratedMethod.builder()
                .method(method.build())
                .javadoc(setterJavadoc(getterJavadoc, name, ""))
                .contentBuilder(contentConsumer)
                .build();
    }

    String generateFromConfigOptional() {
        TypeName optionalType = type().typeArguments().get(0);
        if (optionalType.fqName().equals("char[]")) {
            return ".asString().as(String::toCharArray)";
        }

        TypeName boxed = optionalType.boxed();
        return ".as(" + boxed.fqName() + ".class)";

    }
}
