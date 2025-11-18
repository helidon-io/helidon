/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen.spi;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.codegen.GeneratedMethod;
import io.helidon.builder.codegen.OptionInfo;
import io.helidon.builder.codegen.OptionMethodType;
import io.helidon.builder.codegen.PrototypeInfo;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;

/**
 * Extension to modify the builder and prototype that is generated.
 */
public interface BuilderCodegenExtension {
    /**
     * An extension can update the prototype information, add annotations to types, add custom methods etc.
     * <p>
     * NOTE: this method CANNOT modify options
     *
     * @param prototypeInfo prototype information from blueprint and previous extensions
     * @return updated prototype information
     */
    default PrototypeInfo prototypeInfo(PrototypeInfo prototypeInfo) {
        return prototypeInfo;
    }

    /**
     * A list of (updated) options to be used during code generation.
     * <p>
     * An option is a property of the prototype, that has a prototype accessor, builder field, builder setter,
     * and builder getter. All of these methods will be code generated. If an option is inherited from a blueprint,
     * it is still code generated, with {@link java.lang.Override} annotation.
     *
     * @param prototypeInfo   prototype information from blueprint and previous extensions
     * @param existingOptions list of options from blueprint and previous extensions
     * @return list of builder options
     */
    default List<OptionInfo> options(PrototypeInfo prototypeInfo, List<OptionInfo> existingOptions) {
        return existingOptions;
    }

    /**
     * Update the prototype interface.
     * This can add additional factory methods, constants, etc.
     * <p>
     * Do not add properties through this method, use {@link #options(io.helidon.builder.codegen.PrototypeInfo, java.util.List)}
     * instead.
     *
     * @param prototypeInfo prototype information
     * @param options       list of options
     * @param classModel    prototype interface class model
     */
    default void updatePrototype(PrototypeInfo prototypeInfo, List<OptionInfo> options, ClassModel.Builder classModel) {
    }

    /**
     * Update the builder base.
     * This can add additional fields, methods, constants, etc.
     *
     * @param prototypeInfo prototype information
     * @param options       list of options
     * @param classModel    builder base class model
     */
    default void updateBuilderBase(PrototypeInfo prototypeInfo, List<OptionInfo> options, ClassBase.Builder<?, ?> classModel) {
    }

    /**
     * Update the {@code preBuildPrototype} method of builder base.
     * This method is called first in the builder hierarchy to handle decorators.
     *
     * @param prototypeInfo prototype information
     * @param options       list of options
     * @param method        method builder
     */
    default void updatePreBuildPrototype(PrototypeInfo prototypeInfo, List<OptionInfo> options, Method.Builder method) {
    }

    /**
     * Update the {@code validatePrototype} method of builder base.
     * This method is called last in the builder hierarchy to handle validation, right before
     * calling the {@code build} method.
     *
     * @param prototypeInfo prototype information
     * @param options       list of options
     * @param method        method builder
     */
    default void updateValidatePrototype(PrototypeInfo prototypeInfo, List<OptionInfo> options, Method.Builder method) {
    }

    /**
     * Update the builder.
     *
     * @param prototypeInfo prototype information
     * @param options       list of options
     * @param classModel    builder class model
     */
    default void updateBuilder(PrototypeInfo prototypeInfo, List<OptionInfo> options, ClassBase.Builder<?, ?> classModel) {
    }

    /**
     * Update the implementation class.
     * <p>
     * Do not add properties through this method, use {@link #options(io.helidon.builder.codegen.PrototypeInfo, java.util.List)}
     * instead.
     *
     * @param prototypeInfo prototype information
     * @param options       list of options
     * @param classModel    implementation class model
     */
    default void updateImplementation(PrototypeInfo prototypeInfo, List<OptionInfo> options, ClassBase.Builder<?, ?> classModel) {
    }

    /**
     * This allows modification to the methods generated for options by the default builder code generator.
     * To add additional methods to any of the generated code, use
     * {@link #prototypeInfo(io.helidon.builder.codegen.PrototypeInfo)}.
     * <p>
     * <strong>Important note:</strong> we may add new method types in minor versions of Helidon, please make sure
     * this would not break your extension.
     *
     * @param option     option information
     * @param method     method to modify, possibly remove, or return
     * @param methodType type of the method being processed
     * @return updated generated method
     */
    default Optional<GeneratedMethod> method(OptionInfo option, GeneratedMethod method, OptionMethodType methodType) {
        return Optional.of(method);
    }
}
