/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.maven.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.DescriptorClassCode;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceContracts;

class MavenRoundContext implements RegistryRoundContext {
    private final List<DescriptorClassCode> descriptors = new ArrayList<>();
    private final MavenCodegenContext ctx;

    MavenRoundContext(MavenCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void addDescriptor(String registryType,
                              TypeName serviceType,
                              TypeName descriptorType,
                              ClassModel.Builder descriptor,
                              double weight,
                              Set<ResolvedType> contracts,
                              Set<ResolvedType> factoryContracts,
                              Object... originatingElements) {
        ClassCode cc = new ClassCode(descriptorType,
                                     descriptor,
                                     serviceType,
                                     originatingElements);
        descriptors.add(DescriptorClassCode.create(cc, registryType, weight, contracts, factoryContracts));
    }

    @Override
    public ServiceContracts serviceContracts(TypeInfo serviceInfo) {
        return ServiceContracts.create(ctx.options(),
                                       this::typeInfo,
                                       serviceInfo);
    }

    @Override
    public Collection<TypeName> availableAnnotations() {
        return List.of();
    }

    @Override
    public Collection<TypeInfo> types() {
        return List.of();
    }

    @Override
    public Collection<TypeInfo> annotatedTypes(TypeName annotationType) {
        return List.of();
    }

    @Override
    public Collection<TypedElementInfo> annotatedElements(TypeName annotationType) {
        return List.of();
    }

    @Override
    public void addGeneratedType(TypeName type,
                                 ClassModel.Builder newClass,
                                 TypeName mainTrigger,
                                 Object... originatingElements) {

    }

    @Override
    public Optional<ClassModel.Builder> generatedType(TypeName type) {
        return Optional.empty();
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName) {
        return ctx.typeInfo(typeName);
    }

    public List<DescriptorClassCode> descriptors() {
        return descriptors;
    }
}
