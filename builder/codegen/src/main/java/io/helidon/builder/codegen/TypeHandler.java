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
import java.util.Optional;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

interface TypeHandler {

    static TypeHandler create(List<BuilderCodegenExtension> extensions, PrototypeInfo prototypeInfo, OptionInfo option) {
        TypeHandlerBasic handler = handler(extensions, prototypeInfo, option);
        handler.prepareMethods();
        return handler;
    }

    Optional<GeneratedMethod> optionMethod(OptionMethodType type);

    /**
     * Create a field for the handled option.
     *
     * @param classBuilder builder of the inner class {@code BuilderBase} or of implementation class
     * @param isBuilder    whether we are generating builder field ({@code true}), or implementation field ({@code false})
     */
    void fields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder);

    /**
     * The type name we use for builder fields. For {@link java.util.Optional}, {@link java.util.Set} and {@link java.util.List},
     * this would be the first type argument.
     *
     * @return type
     */
    TypeName type();

    /**
     * Add builder base setters for this option.
     *
     * @param classBuilder builder of the inner class {@code BuilderBase}
     */
    void setters(InnerClass.Builder classBuilder);

    /**
     * Generate from config section for this option.
     * The config instance is provided under name {@code config}.
     *
     * @param builder          method builder
     * @param optionConfigured configured information for this option
     */
    void generateFromConfig(Method.Builder builder, OptionConfigured optionConfigured);

    /**
     * Whether the builder getter returns an {@link java.util.Optional}.
     *
     * @return if return optional from builder getter
     */
    boolean builderGetterOptional();

    /**
     * Generate assignment from an existing builder.
     * <p>
     * Builder parameter name is {@code builder}.
     *
     * @param contentBuilder content builder of the method
     */
    void fromBuilderAssignment(ContentBuilder<?> contentBuilder);

    /**
     * Generate assignment from an existing prototype instance.
     * <p>
     * Builder parameter name is {@code prototype}.
     *
     * @param contentBuilder content builder of the method
     */
    void fromPrototypeAssignment(ContentBuilder<?> contentBuilder);

    private static TypeHandlerBasic handler(List<BuilderCodegenExtension> extensions,
                                            PrototypeInfo prototypeInfo,
                                            OptionInfo option) {
        var declaredType = option.declaredType();

        if (declaredType.isOptional()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.OPTIONAL, 1);
            return new TypeHandlerOptional(extensions, prototypeInfo, option);
        }
        if (declaredType.isSupplier()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.SUPPLIER, 1);
            return new TypeHandlerSupplier(extensions, prototypeInfo, option);
        }
        if (declaredType.isSet()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.SET, 1);
            return new TypeHandlerSet(extensions, prototypeInfo, option);
        }
        if (declaredType.isList()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.LIST, 1);
            return new TypeHandlerList(extensions, prototypeInfo, option);
        }
        if (declaredType.isMap()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.MAP, 2);
            return new TypeHandlerMap(extensions, prototypeInfo, option);
        }
        return new TypeHandlerBasic(extensions, prototypeInfo, option, option.declaredType());
    }

    private static void checkTypeArgsSizeAndTypes(PrototypeInfo prototypeInfo,
                                                  OptionInfo option,
                                                  TypeName declaredType,
                                                  int expectedTypeArgs) {
        Object originatingElement;
        if (option.interfaceMethod().isPresent()) {
            originatingElement = option.interfaceMethod().get().originatingElementValue();
        } else {
            originatingElement = prototypeInfo.blueprint().originatingElementValue();
        }
        List<TypeName> typeNames = option.declaredType().typeArguments();
        if (typeNames.size() != expectedTypeArgs) {
            throw new CodegenException("Option of type " + declaredType.fqName() + " must have " + expectedTypeArgs
                                               + " type arguments defined, but option \"" + option.name() + "\" does not",
                                       originatingElement);
        }
        for (TypeName typeName : typeNames) {
            if (typeName.wildcard()) {
                throw new CodegenException("Property of type " + option.declaredType().resolvedName()
                                                   + " is not supported for builder,"
                                                   + " as wildcards cannot be handled correctly in setters",
                                           originatingElement);
            }
        }
    }
}
