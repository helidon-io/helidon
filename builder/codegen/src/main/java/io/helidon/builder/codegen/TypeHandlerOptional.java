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
import java.util.function.Consumer;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.codegen.CodegenUtil.capitalize;

class TypeHandlerOptional extends TypeHandlerBasic {
    TypeHandlerOptional(List<BuilderCodegenExtension> extensions, PrototypeInfo prototypeInfo, OptionInfo option) {
        super(extensions, prototypeInfo, option, firstTypeArgument(option));
    }

    @Override
    public void fields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder) {
        Field.Builder builder = Field.builder()
                .isFinal(!isBuilder)
                .name(option().name());

        TypeName usedType = isBuilder ? type() : option().declaredType();

        if (isBuilder && (option().required() || option().defaultValue().isEmpty())) {
            // we need to use object types to be able to see if this was configured
            builder.type(usedType.boxed());
        } else {
            builder.type(usedType);
        }

        if (isBuilder && option().defaultValue().isPresent()) {
            option().defaultValue().get().accept(builder);
        }

        classBuilder.addField(builder);
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSetterDeclared(Javadoc getterJavadoc) {
        TypeName typeName = asTypeArgument(TypeNames.OPTIONAL);
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();
        boolean generic = typeName.typeArguments().getFirst().wildcard();

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

            it.addContent("this." + name + " = " + name);
            if (generic) {
                it.addContent(".map(")
                        .addContent(type().genericTypeName())
                        .addContent(".class::cast)");
            }

            it.addContent(".orElse(this.")
                    .addContent(option().name())
                    .addContentLine(");")
                    .addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .javadoc(setterJavadoc(getterJavadoc, name, ""))
                                           .contentBuilder(contentConsumer)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderClear(Javadoc getterJavadoc) {
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        Javadoc javadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "ignore", ""))
                .content(List.of("Clear existing value of " + name + "."))
                .parameters(Map.of())
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName("clear" + capitalize(option().name()))
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        Consumer<ContentBuilder<?>> contentConsumer = it -> {

            option().decorator()
                    .ifPresent(decorator -> it.addContent("new ")
                            .addContent(decorator)
                            .addContent("().decorate(this, ")
                            .addContent(Optional.class)
                            .addContentLine(".empty());"));

            it.addContentLine("this." + name + " = null;")
                    .addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    void decorateValue(ContentBuilder<?> contentBuilder, String optionName) {
        contentBuilder.addContent(TypeNames.OPTIONAL)
                .addContent(".of(")
                .addContent(optionName)
                .addContent(")");
    }
}
