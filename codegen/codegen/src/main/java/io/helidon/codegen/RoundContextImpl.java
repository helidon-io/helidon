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

package io.helidon.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

class RoundContextImpl implements RoundContext {
    private final Map<TypeName, ClassCode> newTypes = new HashMap<>();
    private final Map<TypeName, List<TypeInfo>> annotationToTypes;
    private final Map<TypeName, Set<TypeName>> metaAnnotated;
    private final List<TypeInfo> types;
    private final CodegenContext ctx;
    private final List<ClassCode> newTypesFromPreviousExtensions;
    private final Collection<TypeName> annotations;

    RoundContextImpl(CodegenContext ctx,
                     List<ClassCode> newTypes,
                     Set<TypeName> annotations,
                     Map<TypeName, List<TypeInfo>> annotationToTypes,
                     Map<TypeName, Set<TypeName>> metaAnnotated,
                     List<TypeInfo> types) {
        this.ctx = ctx;
        this.newTypesFromPreviousExtensions = newTypes;
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
            if (typeInfo.hasAnnotation(annotationType) || TypeHierarchy.hierarchyAnnotations(ctx, typeInfo)
                    .stream()
                    .anyMatch(it -> it.typeName().equals(annotationType))) {
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
    public void addGeneratedType(TypeName type,
                                 ClassModel.Builder newClass,
                                 TypeName mainTrigger,
                                 Object... originatingElements) {
        this.newTypes.put(type, new ClassCode(type, newClass, mainTrigger, originatingElements));
    }

    @Override
    public Optional<ClassModel.Builder> generatedType(TypeName type) {
        return Optional.ofNullable(newTypes.get(type)).map(ClassCode::classModel);
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName) {
        var found = ctx.typeInfo(typeName);
        if (found.isPresent()) {
            return found;
        }

        return generatedClass(typeName)
                .map(it -> ClassModelFactory.create(
                        this,
                        typeName,
                        it));
    }

    private Optional<ClassBase> generatedClass(TypeName typeName) {
        for (ClassCode classCode : newTypes.values()) {
            Optional<ClassBase> inProgress = classCode.classModel().find(typeName);
            if (inProgress.isPresent()) {
                return inProgress;
            }
        }
        for (ClassCode classCode : newTypesFromPreviousExtensions) {
            Optional<ClassBase> inProgress = classCode.classModel().find(typeName);
            if (inProgress.isPresent()) {
                return inProgress;
            }
        }

        if (!typeName.packageName().isEmpty()) {
            return Optional.empty();
        }

        // this is most likely a type that is generated,
        // so there is a good chance it was generated before, let's try
        // with package name(s) of the generated types
        // default (empty) package should not be used by programs, so we can make this bold assumption

        for (var classCode : newTypes.values()) {
            String packageName = classCode.newType().packageName();
            TypeName toFind = TypeName.builder(typeName)
                    .packageName(packageName)
                    .build();
            var inProgress = classCode.classModel().find(toFind);
            if (inProgress.isPresent()) {
                return inProgress;
            }
        }

        for (var classCode : newTypesFromPreviousExtensions) {
            String packageName = classCode.newType().packageName();
            TypeName toFind = TypeName.builder(typeName)
                    .packageName(packageName)
                    .build();
            var inProgress = classCode.classModel().find(toFind);
            if (inProgress.isPresent()) {
                return inProgress;
            }
        }

        return Optional.empty();
    }

    Collection<ClassCode> newTypes() {
        return newTypes.values();
    }
}
