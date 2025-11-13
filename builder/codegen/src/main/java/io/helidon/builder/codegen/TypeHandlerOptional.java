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

import java.util.Objects;
import java.util.Optional;

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
import static io.helidon.common.types.TypeNames.OPTIONAL;

class TypeHandlerOptional extends TypeHandlerBase {
    TypeHandlerOptional(PrototypeInfo prototypeInfo, OptionInfo option) {
        super(prototypeInfo, option, firstTypeArgument(option));
    }

    @Override
    public Field.Builder field(boolean isBuilder) {
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

        return builder;
    }

    @Override
    public void setters(InnerClass.Builder classBuilder, TypeName returnType) {
        declaredSetter(classBuilder, returnType);
        clearSetter(classBuilder, returnType);

        // and add the setter with the actual type
        // config is special - handled directly when configuration is handled, as it also must be used when this type
        // is @Configured
        if (isConfigProperty()) {
            return;
        }

        var setter = option().setter();
        String optionName = option().name();

        // declared setter - optional is package local, field is never optional in builder
        Method.Builder method = Method.builder()
                .name(setter.elementName())
                .accessModifier(setter.accessModifier())
                .javadoc(Javadoc.parse(setter.description().orElse("")))
                .returnType(returnType)
                .addParameter(param -> param.name(optionName)
                        .type(type().unboxed()))
                .update(it -> {
                    if (!type().unboxed().primitive()) {
                        it.addContent(Objects.class)
                                .addContentLine(".requireNonNull(" + optionName + ");");
                    }
                })
                .update(it -> option().decorator()
                        .ifPresent(decorator ->
                                           it.addContent("new ")
                                                   .addContent(decorator)
                                                   .addContent("().decorate(this, ")
                                                   .addContent(Optional.class)
                                                   .addContent(".of(")
                                                   .addContent(optionName)
                                                   .addContentLine("));")))
                .addContentLine("this." + optionName + " = " + optionName + ";")
                .addContentLine("return self();");
        classBuilder.addMethod(method);

        if (type().equals(CHAR_ARRAY)) {
            charArraySetter(classBuilder, returnType);
        }

        if (option().builderInfo().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            builderConsumerSetter(classBuilder, returnType, option().builderInfo().get());
        }
    }

    @Override
    TypeName setterArgumentTypeName() {
        TypeName type = type();
        if (TypeNames.STRING.equals(type) || type.unboxed().primitive() || type.array()) {
            return option().declaredType();
        }

        return TypeName.builder(OPTIONAL)
                .addTypeArgument(toWildcard(type))
                .build();
    }

    @Override
    void declaredSetter(InnerClass.Builder classBuilder, TypeName returnType) {
        TypedElementInfo setter = option().setter();

        boolean generic = !type().typeArguments().isEmpty();
        // declared setter - optional is package local, field is never optional in builder
        classBuilder.addMethod(builder -> builder.name(setter.elementName())
                .update(it -> {
                    if (generic) {
                        it.addAnnotation(Annotation.create(SuppressWarnings.class, "unchecked"));
                    }
                })
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .javadoc(Javadoc.parse(setter.description().orElse("")))
                .returnType(returnType)
                .addParameter(param -> param.name(option().name())
                        .type(setterArgumentTypeName()))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + option().name() + ");")
                .addContentLine("this." + option().name() + " = " + option().name()
                                        + ".map(" + type().fqName() + ".class::cast)"
                                        + ".orElse(this." + option().name() + ");")
                .addContentLine("return self();"));
    }

    private void clearSetter(InnerClass.Builder classBuilder, TypeName returnType) {
        // declared setter - optional is package local, field is never optional in builder
        TypedElementInfo setter = option().setter();

        classBuilder.addMethod(builder -> builder.name("clear" + capitalize(option().name()))
                .accessModifier(setter.accessModifier())
                .description("Clear existing value of this property.")
                .returnType(returnType, "updated builder instance")
                .addJavadocTag("see", "#" + option().getter().elementName() + "()")
                .update(it -> option().decorator()
                        .ifPresent(decorator -> it.addContent("new ")
                                .addContent("new ")
                                .addContent(decorator)
                                .addContent("().decorate(this, ")
                                .addContent(Optional.class)
                                .addContentLine(".empty());")))
                .addContentLine("this." + option().name() + " = null;")
                .addContentLine("return self();"));
    }
}
