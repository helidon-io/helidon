/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.processor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;
import io.helidon.config.metadata.processor.ConfiguredType.ProducerMethod;

import static io.helidon.config.metadata.processor.UsedTypes.BUILDER;
import static io.helidon.config.metadata.processor.UsedTypes.COMMON_CONFIG;
import static io.helidon.config.metadata.processor.UsedTypes.CONFIG;
import static io.helidon.config.metadata.processor.UsedTypes.META_CONFIGURED;

/*
 * Takes care of blueprints annotated with builder API only.
 */
class MetadataHandlerConfig implements MetadataHandler {
    private final TypeInfo typeInfo;
    private final TypeName typeName;

    MetadataHandlerConfig(TypeInfo typeInfo) {
        this.typeInfo = typeInfo;
        this.typeName = typeInfo.typeName();
    }

    @Override
    public MetadataHandlerResult handle() {
        TypeName targetType;
        boolean isBuilder;
        String module;
        Optional<TypeInfo> foundTarget = findBuilderTarget(new HashSet<>(), typeInfo);
        ConfiguredAnnotation configured = ConfiguredAnnotation.create(typeInfo.annotation(META_CONFIGURED));
        if (!configured.ignoreBuildMethod() && foundTarget.isPresent()) {
            // this is a builder, we need the target type
            targetType = foundTarget.get().typeName();
            isBuilder = true;
            module = foundTarget.get().module().orElse("unknown");
        } else {
            targetType = typeName;
            isBuilder = false;
            module = typeInfo.module().orElse("unknown");
        }

        /*
          now we know whether this is
          - a builder + known target class (result of builder() method)
          - a standalone class (probably with public static create(Config) method)
          - an interface/abstract class only used for inheritance
         */

        ConfiguredType type = new ConfiguredType(configured,
                                                 typeName,
                                                 targetType,
                                                 false);

        /*
         we also need to know all superclasses / interfaces that are configurable so we can reference them
         these may be from other modules, so we cannot create a single set of values from all types
         */
        addSuperClasses(type, typeInfo);
        addInterfaces(type, typeInfo);

        if (isBuilder) {
            // builder
            processBuilderType(typeInfo, type, typeName, targetType);
        } else {
            // standalone class with create method(s), or interface/abstract class
            processTargetType(typeInfo, type, typeName, type.standalone());
        }

        return new MetadataHandlerResult(targetType, module, type);
    }

    private void processBuilderType(TypeInfo typeInfo, ConfiguredType type, TypeName typeName, TypeName targetType) {
        type.addProducer(new ProducerMethod(false, typeName.fqName(), "build", new String[0]));

        // check if static TargetType create(Config) exists
        if (typeInfo.elementInfo()
                .stream()
                .filter(it -> it.modifiers().contains(TypeValues.MODIFIER_STATIC))
                .filter(it -> it.modifiers().contains(TypeValues.MODIFIER_PUBLIC))
                .filter(it -> "create".equals(it.elementName()))
                .filter(it -> it.parameterArguments().size() == 1)
                .filter(it -> {
                    TypeName argumentType = it.parameterArguments().get(0).typeName();
                    return CONFIG.equals(argumentType) || COMMON_CONFIG.equals(argumentType);
                })
                .anyMatch(it -> targetType.genericTypeName().equals(it.typeName().genericTypeName()))) {
            type.addProducer(new ProducerMethod(true,
                                                targetType,
                                                "create",
                                                new String[] {}))
        }
    }

    private Optional<TypeInfo> findBuilderTarget(Set<TypeName> processed, TypeInfo typeInfo) {
        TypeName typeName = typeInfo.typeName();
        if (processed.contains(typeName)) {
            // never do the same type twice, as we may end in endless loop
            return Optional.empty();
        }

        if (BUILDER.equals(typeName.genericTypeName())) {
            if (typeName.typeArguments().size() == 2) {
                return Optional.of(typeInfo);
            }
        }

        Optional<TypeInfo> found = typeInfo.interfaceTypeInfo()
                .stream()
                .filter(it -> BUILDER.equals(it.typeName().genericTypeName()))
                .filter(it -> it.typeName().typeArguments().size() == 2)
                .findFirst();

        if (found.isPresent()) {
            return found;
        }

        // let's check super type
        Optional<TypeInfo> superType = typeInfo.superTypeInfo();
        if (superType.isPresent()) {
            found = findBuilderTarget(processed, superType.get());
            if (found.isPresent()) {
                return found;
            }
        }

        // and interfaces of interfaces
        for (TypeInfo interfaceType : typeInfo.interfaceTypeInfo()) {
            found = findBuilderTarget(processed, interfaceType);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private void addSuperClasses(ConfiguredType type, TypeInfo typeInfo) {
        Optional<TypeInfo> foundSuperType = typeInfo.superTypeInfo();
        if (foundSuperType.isEmpty()) {
            return;
        }
        TypeInfo superClass = foundSuperType.get();

        while (true) {
            if (superClass.hasAnnotation(META_CONFIGURED)) {
                // we only care about the first one. This one should reference its superclass/interfaces
                // if they are configured as well
                type.addInherited(superClass.typeName());
                return;
            }

            foundSuperType = superClass.superTypeInfo();
            if (foundSuperType.isEmpty()) {
                return;
            }
            superClass = foundSuperType.get();
        }
    }

    private void addInterfaces(ConfiguredType type, TypeInfo typeInfo) {
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            if (interfaceInfo.hasAnnotation(META_CONFIGURED)) {
                type.addInherited(interfaceInfo.typeName());
            } else {
                addSuperClasses(type, interfaceInfo);
            }
        }
    }

}
