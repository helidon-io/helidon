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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

class RoundContextImpl implements RegistryRoundContext {
    private final RegistryCodegenContext ctx;
    private final RoundContext delegate;
    private final Map<TypeName, List<TypeInfo>> annotationToTypes;
    private final Map<TypeName, Set<TypeName>> metaAnnotated;
    private final List<TypeInfo> types;
    private final Collection<TypeName> annotations;

    RoundContextImpl(RegistryCodegenContext ctx,
                     RoundContext delegate,
                     Set<TypeName> annotations,
                     Map<TypeName, List<TypeInfo>> annotationToTypes,
                     Map<TypeName, Set<TypeName>> metaAnnotated,
                     List<TypeInfo> types) {
        this.ctx = ctx;
        this.delegate = delegate;

        this.annotations = annotations;
        this.annotationToTypes = annotationToTypes;
        this.metaAnnotated = metaAnnotated;
        this.types = types;
    }

    @Override
    public Collection<TypeName> availableAnnotations() {
        return annotations;
    }

    @Override
    public Collection<TypeInfo> types() {
        return types;
    }

    @Override
    public Collection<TypedElementInfo> annotatedElements(TypeName annotationType) {
        List<TypeInfo> typeInfos = annotationToTypes.get(annotationType);
        if (typeInfos == null) {
            return Set.of();
        }

        List<TypedElementInfo> result = new ArrayList<>();

        for (TypeInfo typeInfo : typeInfos) {
            typeInfo.elementInfo()
                    .stream()
                    .filter(it -> it.hasAnnotation(annotationType))
                    .forEach(result::add);
        }

        return result;
    }

    @Override
    public Collection<TypeInfo> annotatedTypes(TypeName annotationType) {
        List<TypeInfo> typeInfos = annotationToTypes.get(annotationType);
        if (typeInfos == null) {
            return Set.of();
        }

        List<TypeInfo> result = new ArrayList<>();

        for (TypeInfo typeInfo : typeInfos) {
            if (typeInfo.hasAnnotation(annotationType)) {
                result.add(typeInfo);
            }
        }

        return result;
    }

    @Override
    public Collection<TypeName> annotatedAnnotations(TypeName metaAnnotation) {
        return Optional.ofNullable(metaAnnotated.get(metaAnnotation)).orElseGet(Set::of);
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName) {
        return delegate.typeInfo(typeName);
    }

    /**
     * Add a non-service descriptor generated type.
     *
     * @param type                type of the new class
     * @param newClass            builder of the new class
     * @param mainTrigger         a type that caused this, may be the processor itself, if not bound to any type
     * @param originatingElements possible originating elements  (such as Element in APT, or ClassInfo in classpath scanning)
     */
    @SuppressWarnings("removal")
    @Override
    public void addGeneratedType(TypeName type,
                                 ClassModel.Builder newClass,
                                 TypeName mainTrigger,
                                 Object... originatingElements) {
        // until we remove the method from context, we must use a single approach, then we can remove the next line
        ctx.types().add(new ClassCode(type, newClass, mainTrigger, originatingElements));
        delegate.addGeneratedType(type, newClass, mainTrigger, originatingElements);
    }

    @SuppressWarnings("removal")
    @Override
    public void addDescriptor(String registryType,
                              TypeName serviceType,
                              TypeName descriptorType,
                              ClassModel.Builder descriptor,
                              double weight,
                              Set<TypeName> contracts,
                              Object... originatingElements) {
        Objects.requireNonNull(registryType);
        Objects.requireNonNull(serviceType);
        Objects.requireNonNull(descriptorType);
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(contracts);
        Objects.requireNonNull(originatingElements);

        // until we remove the method from context, we must use a single approach, then we can have descriptors stored
        // in the extension, and reset after each round
        ctx.descriptors()
                .add(new DescriptorClassCodeImpl(new ClassCode(descriptorType, descriptor, serviceType, originatingElements),
                                                    registryType,
                                                    weight,
                                                    contracts));
        delegate.addGeneratedType(descriptorType, descriptor, serviceType, originatingElements);
    }

    @SuppressWarnings("removal")
    @Override
    public List<DescriptorClassCode> descriptors() {
        return ctx.descriptors();
    }

    @Override
    public Optional<ClassModel.Builder> generatedType(TypeName type) {
        return delegate.generatedType(type);
    }
}
