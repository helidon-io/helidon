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

package io.helidon.service.codegen;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypeName;

/**
 * Codegen context adding methods suitable for Helidon Service Registry code generation.
 */
public interface RegistryCodegenContext extends CodegenContext {
    /**
     * Create a new instance from an existing code generation context.
     *
     * @param context code generation context of the current code generation session
     * @return a new Helidon Service Registry code generation context
     */
    static RegistryCodegenContext create(CodegenContext context) {
        return new RegistryCodegenContextImpl(context);
    }

    /**
     * Service descriptor of a type that is already created. This allows extensions with lower weight to update
     * the code generated descriptor after it was generated.
     *
     * @param serviceType type of the service (the implementation class we generate descriptor for)
     * @return the builder of class model, if the service has a descriptor
     */
    Optional<ClassModel.Builder> descriptor(TypeName serviceType);

    /**
     * Class mode for a type that is to be code generated (either a {@link #type(io.helidon.common.types.TypeName)}
     * or {@link #descriptor(io.helidon.common.types.TypeName)}, but also returns inner class if it matches.
     *
     * @param type type to look for
     * @return top level or inner class matching the type if any
     */
    Optional<ClassBase> generatedClass(TypeName type);

    /**
     * Add a new service descriptor.
     *
     * @param registryType        service registry this descriptor is designed for (core is the "top" level)
     * @param serviceType         type of the service (the implementation class we generate descriptor for)
     * @param descriptorType      type of the service descriptor
     * @param descriptor          descriptor class model
     * @param weight              weight of this service descriptor
     * @param contracts           contracts of this service descriptor
     * @param originatingElements possible originating elements (such as Element in APT, or ClassInfo in classpath scanning)
     * @throws java.lang.IllegalStateException if an attempt is done to register a new descriptor for the same type
     * @deprecated use
     *         {@link io.helidon.service.codegen.RegistryRoundContext#addDescriptor(String, io.helidon.common.types.TypeName, io.helidon.common.types.TypeName, io.helidon.codegen.classmodel.ClassModel.Builder, double, java.util.Set, Object...)}
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    void addDescriptor(String registryType,
                       TypeName serviceType,
                       TypeName descriptorType,
                       ClassModel.Builder descriptor,
                       double weight,
                       Set<TypeName> contracts,
                       Object... originatingElements);

    /**
     * Add a new class to be code generated.
     *
     * @param type                type of the new class
     * @param newClass            builder of the new class
     * @param mainTrigger         a type that caused this, may be the processor itself, if not bound to any type
     * @param originatingElements possible originating elements  (such as Element in APT, or ClassInfo in classpath scanning)
     * @deprecated use
     *         {@link io.helidon.service.codegen.RegistryRoundContext#addGeneratedType(io.helidon.common.types.TypeName,
     *         io.helidon.codegen.classmodel.ClassModel.Builder, io.helidon.common.types.TypeName, Object...)}
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    void addType(TypeName type, ClassModel.Builder newClass, TypeName mainTrigger, Object... originatingElements);

    /**
     * Class for a type.
     *
     * @param type type of the generated type
     * @return class model of the new type if any
     */
    Optional<ClassModel.Builder> type(TypeName type);

    /**
     * Create a descriptor type for a service.
     *
     * @param serviceType type of the service
     * @return type of the service descriptor to be generated
     */
    TypeName descriptorType(TypeName serviceType);

    /**
     * All newly generated types.
     *
     * @return list of types and their source class model
     * @deprecated use {@link io.helidon.codegen.RoundContext#generatedType(io.helidon.common.types.TypeName)} instead to get a
     *  single type; otherwise this is no longer available (kindly file an issue if this method is needed)
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    List<ClassCode> types();

    /**
     * All newly generated descriptors.
     *
     * @return list of descriptors and their source class model
     * @deprecated use {@link RegistryRoundContext#descriptors()} instead
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    List<DescriptorClassCode> descriptors();

    /**
     * This provides support for replacements of types.
     *
     * @param typeName    type name as required by the dependency ("injection point")
     * @param valueSource code with the source of the parameter as Helidon provides it (such as Supplier of type)
     * @return assignment to use for this instance, what type to use in Helidon registry, and code generator to transform to
     *         desired type
     */
    Assignment assignment(TypeName typeName, String valueSource);

    /**
     * Assignment for code generation. The original intended purpose is to support {@code Provider} from javax and jakarta
     * without a dependency (or need to understand it) in the generator code.
     *
     * @param usedType      type to use as the dependency type using only Helidon supported types
     *                      (i.e. {@link java.util.function.Supplier} instead of jakarta {@code Provider}
     * @param codeGenerator code generator that creates appropriate type required by the target
     */
    record Assignment(TypeName usedType, Consumer<ContentBuilder<?>> codeGenerator) {
    }
}
