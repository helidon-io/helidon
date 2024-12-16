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

package io.helidon.config.metadata.processor;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.processing.Messager;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import io.helidon.common.processor.ElementInfoPredicates;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.config.metadata.processor.TypeHandlerMetaApiBase.OptionType;

import static io.helidon.config.metadata.processor.UsedTypes.BLUEPRINT;
import static io.helidon.config.metadata.processor.UsedTypes.COMMON_CONFIG;
import static io.helidon.config.metadata.processor.UsedTypes.PROTOTYPE_FACTORY;

final class BlueprintUtil {
    private BlueprintUtil() {
    }

    static void addInterfaces(ConfiguredType type,
                              TypeInfo typeInfo,
                              TypeName requiredAnnotation) {
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            if (interfaceInfo.hasAnnotation(requiredAnnotation)) {
                TypeName ifaceTypeName = interfaceInfo.typeName();

                if (interfaceInfo.hasAnnotation(BLUEPRINT)) {
                    String className = ifaceTypeName.className();
                    if (className.endsWith("Blueprint")) {
                        className = className.substring(0, className.length() - "Blueprint".length());
                    }
                    ifaceTypeName = TypeName.builder(ifaceTypeName)
                            .className(className)
                            .build();
                }

                type.addInherited(ifaceTypeName);
            } else {
                addSuperClasses(type, interfaceInfo, requiredAnnotation);
            }
        }
    }

    // if the type implements `Factory<X>`, we want to return X, otherwise "pure" config object
    static TypeName targetType(TypeInfo blueprint, TypeName prototype) {
        return blueprint.interfaceTypeInfo()
                .stream()
                .map(TypeInfo::typeName)
                .filter(it -> PROTOTYPE_FACTORY.equals(it.genericTypeName()))
                .filter(it -> it.typeArguments().size() == 1)
                .map(it -> it.typeArguments().get(0))
                .findAny()
                .orElse(prototype);
    }

    static TypeName prototype(TypeName blueprintType) {
        String className = blueprintType.className();
        if (className.endsWith("Blueprint")) {
            className = className.substring(0, className.length() - "Blueprint".length());
        }
        return TypeName.builder(blueprintType)
                .className(className)
                .build()
                .genericTypeName();
    }

    static void processBlueprint(TypeInfo blueprint,
                                 ConfiguredType type,
                                 TypeName prototype,
                                 TypeName targetType,
                                 TypeName requiredOptionAnnotation,
                                 Consumer<TypedElementInfo> methodHandler) {
        type.addProducer(new ConfiguredType.ProducerMethod(true, prototype, "create", List.of(COMMON_CONFIG)));
        type.addProducer(new ConfiguredType.ProducerMethod(true, prototype, "builder", List.of()));

        if (!targetType.equals(prototype)) {
            // target type may not have the method (if it is for example PrivateKey)
            type.addProducer(new ConfiguredType.ProducerMethod(false, targetType, "create", List.of(prototype)));
        }

        // and now process all blueprint methods - must be non default, non static
        // methods on this interface

        blueprint.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(TypeHandlerBase.isMine(blueprint.typeName()))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(Predicate.not(ElementInfoPredicates::isDefault))
                .filter(ElementInfoPredicates.hasAnnotation(requiredOptionAnnotation))
                .forEach(methodHandler);
    }

    static OptionType typeForBlueprintFromSignature(Messager aptMessager,
                                                    Elements aptElements,
                                                    TypedElementInfo element,
                                                    ConfiguredOptionData annotation) {
        // guess from method

        if (!ElementInfoPredicates.hasNoArgs(element)) {
            aptMessager.printMessage(Diagnostic.Kind.ERROR, "Method " + element + " is annotated with @Configured, "
                                             + "yet it has a parameter. Interface methods must not have parameters.",
                                     TypeHandlerBase.elementFor(aptElements, element));
            throw new IllegalStateException("Could not determine property type");
        }

        TypeName returnType = element.typeName();
        if (ElementInfoPredicates.isVoid(element)) {
            aptMessager.printMessage(Diagnostic.Kind.ERROR, "Method " + element + " is annotated with @Configured, "
                                             + "yet it is void. Interface methods must return the property type.",
                                     TypeHandlerBase.elementFor(aptElements, element));
            throw new IllegalStateException("Could not determine property type");
        }

        if (returnType.isOptional()) {
            // may be an optional of list etc.
            if (!(returnType.isMap() || returnType.isSet() || returnType.isList())) {
                return new OptionType(returnType.typeArguments().get(0), "VALUE");
            }
            returnType = returnType.typeArguments().get(0);
        }

        if (returnType.isList() || returnType.isSet()) {
            return new OptionType(returnType.typeArguments().get(0), "LIST");
        }

        if (returnType.isMap()) {
            return new OptionType(returnType.typeArguments().get(1), "MAP");
        }

        return new OptionType(returnType.boxed(), annotation.kind());
    }

    private static void addSuperClasses(ConfiguredType type, TypeInfo typeInfo, TypeName requiredAnnotation) {
        Optional<TypeInfo> foundSuperType = typeInfo.superTypeInfo();
        if (foundSuperType.isEmpty()) {
            return;
        }
        TypeInfo superClass = foundSuperType.get();

        while (true) {
            if (superClass.hasAnnotation(requiredAnnotation)) {
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
}
