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
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

class RoundContextImpl implements RegistryRoundContext {
    private final Map<TypeName, List<TypeInfo>> annotationToTypes;
    private final Map<TypeName, Set<TypeName>> metaAnnotated;
    private final List<TypeInfo> types;
    private final Collection<TypeName> annotations;

    RoundContextImpl(Set<TypeName> annotations,
                     Map<TypeName, List<TypeInfo>> annotationToTypes,
                     Map<TypeName, Set<TypeName>> metaAnnotated,
                     List<TypeInfo> types) {

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
}
