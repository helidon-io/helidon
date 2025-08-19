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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG;
import static io.helidon.builder.codegen.Types.PROTOTYPE_API;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY_METHOD;
import static io.helidon.builder.codegen.Types.RUNTIME_API;
import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.codegen.ElementInfoPredicates.elementName;
import static io.helidon.common.types.TypeNames.OBJECT;

/*
 We need the following factory methods:
 1. RuntimeType create(Prototype) (either on Blueprint, or on RuntimeType)
 2. Prototype create(Config) (either on Blueprint, or on ConfigObject)
 */

/**
 * Factory methods for a specific prototype property.
 *
 * @param createTargetType factory method to create runtime type
 * @param createFromConfig
 * @param builder
 */
record FactoryMethods(Optional<FactoryMethod> createTargetType,
                      Optional<FactoryMethod> createFromConfig,
                      Optional<FactoryMethod> builder) {
    static FactoryMethods create(CodegenContext ctx,
                                 TypeInfo blueprint,
                                 TypeHandler typeHandler) {

        Optional<FactoryMethod> targetFactory = targetTypeMethod(ctx, blueprint, typeHandler);
        Set<TypeName> configObjectCandidates = new LinkedHashSet<>();
        if (targetFactory.isPresent()) {
            configObjectCandidates.add(targetFactory.get().argumentType());
        }
        configObjectCandidates.add(typeHandler.actualType());
        configObjectCandidates.add(typeHandler.declaredType());

        // the candidate from factory method is first, as it is more significant
        Optional<FactoryMethod> configFactory = createFromConfigMethod(ctx,
                                                                       blueprint,
                                                                       typeHandler,
                                                                       configObjectCandidates);
        configObjectCandidates = new LinkedHashSet<>();
        if (targetFactory.isPresent()) {
            configObjectCandidates.add(targetFactory.get().argumentType());
        }
        if (configFactory.isPresent()) {
            configObjectCandidates.add(configFactory.get().factoryMethodReturnType());
        }

        return new FactoryMethods(targetFactory,
                                  configFactory,
                                  builder(ctx, typeHandler, configObjectCandidates));
    }

    private static Optional<FactoryMethod> builder(CodegenContext ctx,
                                                   TypeHandler typeHandler,
                                                   Set<TypeName> builderCandidates) {
        if (typeHandler.actualType().equals(OBJECT)
                || typeHandler.actualType().primitive()
                || typeHandler.actualType().generic()) {
            return Optional.empty();
        }

        builderCandidates.add(typeHandler.actualType());
        FactoryMethod found = null;
        FactoryMethod secondary = null;
        for (TypeName builderCandidate : builderCandidates) {

            TypeInfo typeInfo = ctx.typeInfo(builderCandidate.genericTypeName()).orElse(null);
            if (typeInfo == null) {
                if (secondary == null) {
                    // this may be part of annotation processing where type info is not available
                    // our assumption is that the type is code generated and is a correct builder, if this assumption
                    // is not correct, we will need to improve this "algorithm" (please file an issue if that happens)
                    if (builderCandidate.fqName().endsWith(".Builder")) {
                        // this is already a builder
                        continue;
                    }
                    TypeName builderTypeName = TypeName.builder(builderCandidate)
                            .className("Builder")
                            .enclosingNames(List.of(builderCandidate.className()))
                            .build();
                    secondary = new FactoryMethod(builderCandidate, builderTypeName, "builder", null);
                }
                continue;
            }

            found = typeInfo.elementInfo()
                    .stream()
                    .filter(ElementInfoPredicates::isMethod)
                    .filter(ElementInfoPredicates::isStatic)
                    .filter(elementName("builder"))
                    .filter(ElementInfoPredicates::hasNoArgs)
                    .filter(it -> it.typeName().className().equals("Builder"))
                    // Has to have public no-param build method returning right type
                    .filter(it -> ctx.typeInfo(it.typeName()).stream()
                            .flatMap(builderTypeInfo -> builderTypeInfo.elementInfo().stream())
                            .filter(ElementInfoPredicates::isMethod)
                            .filter(ElementInfoPredicates::isPublic)
                            .filter(ElementInfoPredicates::hasNoArgs)
                            .filter(m -> m.typeName().equals(typeInfo.typeName()))
                            .anyMatch(elementName("build")))
                    .findFirst()
                    .map(it -> new FactoryMethod(builderCandidate,
                                                 copyGenericTypes(builderCandidate, it.typeName()),
                                                 "builder",
                                                 null))
                    .orElse(null);
            if (found != null) {
                break;
            }
        }

        FactoryMethod secondaryMethod = secondary;
        return Optional.ofNullable(found).or(() -> Optional.ofNullable(secondaryMethod));
    }

    private static TypeName copyGenericTypes(TypeName from, TypeName to) {
        if (from.typeArguments().isEmpty()) {
            return to;
        }
        return TypeName.builder(to)
                .typeArguments(from.typeArguments())
                .build();
    }

    private static Optional<FactoryMethod> createFromConfigMethod(CodegenContext ctx,
                                                                  TypeInfo blueprint,
                                                                  TypeHandler typeHandler,
                                                                  Set<TypeName> configObjectCandidates) {

        // first look at declared type and blueprint
        String methodName = "create" + capitalize(typeHandler.name());

        // check the blueprint itself, and then check the custom methods type
        Optional<TypeName> returnType = findFactoryMethodByParamType(blueprint,
                                                                     CONFIG,
                                                                     methodName)
                .or(() -> findFactoryMethodByParamType(blueprint,
                                                       COMMON_CONFIG,
                                                       methodName));

        TypeName typeWithFactoryMethod = blueprint.typeName();

        if (returnType.isEmpty()) {
            if (blueprint.hasAnnotation(Types.PROTOTYPE_CUSTOM_METHODS)) {
                Optional<TypeInfo> typeInfo = blueprint.annotation(Types.PROTOTYPE_CUSTOM_METHODS)
                        .typeValue()
                        .flatMap(ctx::typeInfo);
                if (typeInfo.isPresent()) {
                    TypeInfo customMethods = typeInfo.get();
                    typeWithFactoryMethod = customMethods.typeName();
                    returnType = findFactoryMethodByParamType(customMethods,
                                                               CONFIG,
                                                               methodName)
                            .or(() -> findFactoryMethodByParamType(customMethods,
                                                                   COMMON_CONFIG,
                                                                   methodName));
                }
            }
        }

        if (returnType.isPresent()) {
            return Optional.of(new FactoryMethod(typeWithFactoryMethod,
                                                 returnType.get(),
                                                 methodName,
                                                 CONFIG));
        }

        // there is no factory method on definition, let's check if the return type itself is a config object

        // factory method
        String createMethod = "create";

        List<TypeInfo> candidates = configObjectCandidates.stream()
                .map(ctx::typeInfo)
                .flatMap(Optional::stream)
                .toList();

        for (TypeInfo typeInfo : candidates) {
            // is this a config object?
            if (doesImplement(typeInfo, PROTOTYPE_API)) {
                // it should have create(Config) with the correct typing
                Optional<FactoryMethod> foundMethod = findMethod(
                        new MethodSignature(typeInfo.typeName(), createMethod, List.of(CONFIG)),
                        typeInfo,
                        ElementInfoPredicates::isStatic)
                        .or(() -> findMethod(
                                new MethodSignature(typeInfo.typeName(), createMethod, List.of(COMMON_CONFIG)),
                                typeInfo,
                                ElementInfoPredicates::isStatic))
                        .map(it -> new FactoryMethod(typeInfo.typeName(),
                                                     typeInfo.typeName(),
                                                     createMethod,
                                                     CONFIG));
                if (foundMethod.isPresent()) {
                    return foundMethod;
                }
            }
        }

        for (TypeInfo typeInfo : candidates) {
            // if the target type implements ConfiguredType, we use the generic parameter of that interface to look for our config
            // look for "implements ConfiguredType"

            if (doesImplement(typeInfo, RUNTIME_API)) {
                // there is no config factory method available for the type that we have
                TypeName candidateTypeName = typeInfo.typeName();
                // we are now interested in a method with signature "static T create(Config)" where T is the type we are handling
                Optional<FactoryMethod> foundMethod = findMethod(
                        new MethodSignature(candidateTypeName, createMethod, List.of(CONFIG)),
                        typeInfo,
                        ElementInfoPredicates::isStatic)
                        .or(() -> findMethod(
                                new MethodSignature(candidateTypeName, createMethod, List.of(COMMON_CONFIG)),
                                typeInfo,
                                ElementInfoPredicates::isStatic))
                        .map(it -> new FactoryMethod(candidateTypeName, candidateTypeName, createMethod, CONFIG));
                if (foundMethod.isPresent()) {
                    return foundMethod;
                }
            }
        }

        // if there is a "public static T create(io.helidon.commmon.config.Config)" method available, just use it
        for (TypeInfo typeInfo : candidates) {
            // similar to above - but we first want to find the best candidate, this is a fallback
            TypeName candidateTypeName = typeInfo.typeName();
            Optional<FactoryMethod> foundMethod = findMethod(
                    new MethodSignature(candidateTypeName, createMethod, List.of(CONFIG)),
                    typeInfo,
                    ElementInfoPredicates::isStatic,
                    ElementInfoPredicates::isPublic)
                    .or(() -> findMethod(
                            new MethodSignature(candidateTypeName, createMethod, List.of(COMMON_CONFIG)),
                            typeInfo,
                            ElementInfoPredicates::isStatic,
                            ElementInfoPredicates::isPublic))
                    .map(it -> new FactoryMethod(candidateTypeName, candidateTypeName, createMethod, CONFIG));
            if (foundMethod.isPresent()) {
                return foundMethod;
            }
        }

        // this a best effort guess - it is a wrong type (we do not have a package)
        // if this ever fails, please file an issue, and we will improve this "algorithm"
        // we can actually find out if a type is not yet generated (it has Kind ERROR on its mirror)
        for (TypeName configObjectCandidate : configObjectCandidates) {
            if (configObjectCandidate.packageName().isEmpty()) {
                // most likely a generated type that is created as part of this round, let's assume it is a config object
                return Optional.of(new FactoryMethod(configObjectCandidate,
                                                     configObjectCandidate,
                                                     "create",
                                                     CONFIG));
            }
        }

        return Optional.empty();
    }

    private static boolean doesImplement(TypeInfo typeInfo, TypeName interfaceType) {
        return typeInfo.interfaceTypeInfo()
                .stream()
                .anyMatch(it -> interfaceType.equals(it.typeName().genericTypeName()));

    }

    private static Optional<FactoryMethod> targetTypeMethod(CodegenContext ctx,
                                                            TypeInfo blueprint,
                                                            TypeHandler typeHandler) {
        // let's look for a method on definition that takes the type

        // first look at declared type and blueprint
        String createMethodName = "create" + capitalize(typeHandler.name());
        TypeName typeWithFactoryMethod = blueprint.typeName();
        TypeName factoryMethodReturnType = typeHandler.declaredType();
        Optional<TypeName> argumentType = findFactoryMethodByReturnType(blueprint,
                                                                        factoryMethodReturnType,
                                                                        createMethodName);

        if (argumentType.isPresent()) {
            return Optional.of(new FactoryMethod(typeWithFactoryMethod,
                                                 factoryMethodReturnType,
                                                 createMethodName,
                                                 argumentType.get()));
        }

        // then look at actual type
        factoryMethodReturnType = typeHandler.actualType();
        argumentType = findFactoryMethodByReturnType(blueprint, factoryMethodReturnType, createMethodName);
        if (argumentType.isPresent()) {
            return Optional.of(new FactoryMethod(typeWithFactoryMethod,
                                                 factoryMethodReturnType,
                                                 createMethodName,
                                                 argumentType.get()));
        }

        // there is no factory method on definition, let's check if the return type itself is a config object

        // if the type we return implements ConfiguredType, we will generate additional setters
        Optional<TypeInfo> configuredTypeInterface = ctx.typeInfo(typeHandler.actualType())
                .flatMap(it -> it.interfaceTypeInfo()
                        .stream()
                        .filter(typeInfo -> RUNTIME_API.equals(typeInfo.typeName().genericTypeName()))
                        .findFirst());

        createMethodName = "create";

        if (configuredTypeInterface.isPresent()) {
            // MyTargetType MyTargetType.create(ConfigObject object)
            factoryMethodReturnType = typeHandler.actualType();
            typeWithFactoryMethod = factoryMethodReturnType;
            TypeName argumentWithGenerics = configuredTypeInterface.get().typeName().typeArguments().get(0);
            if (!argumentWithGenerics.typeParameters().isEmpty() || !argumentWithGenerics.typeArguments().isEmpty()) {
                if (!factoryMethodReturnType.typeArguments().isEmpty()) {
                    TypeName finalFactoryMethodReturnType = factoryMethodReturnType;
                    argumentWithGenerics = TypeName.builder(argumentWithGenerics)
                            .typeArguments(List.of())
                            .typeParameters(List.of())
                            .update(it -> finalFactoryMethodReturnType.typeArguments().forEach(it::addTypeArgument))
                            .build();
                }
            }
            argumentType = Optional.of(argumentWithGenerics);

            return Optional.of(new FactoryMethod(typeWithFactoryMethod,
                                                 factoryMethodReturnType,
                                                 createMethodName,
                                                 argumentType.get()));
        }

        // and finally we should have the factory method of the actual type we return

        return Optional.empty();
    }

    private static Optional<TypeName> findFactoryMethodByReturnType(TypeInfo declaringType,
                                                                    TypeName returnType,
                                                                    String methodName) {
        return declaringType.elementInfo()
                .stream()
                // methods
                .filter(ElementInfoPredicates::isMethod)
                // static
                .filter(ElementInfoPredicates::isStatic)
                // @FactoryMethod
                .filter(it -> it.hasAnnotation(PROTOTYPE_FACTORY_METHOD))
                // createMyProperty
                .filter(it -> methodName.equals(it.elementName()))
                // returns the same type that is the method return type
                .filter(it -> it.typeName().equals(returnType))
                // if all of the above is true, we use the parameters as the config type
                .filter(it -> it.parameterArguments().size() == 1)
                .map(it -> it.parameterArguments().get(0))
                .map(TypedElementInfo::typeName)
                .findFirst();
    }

    private static Optional<TypeName> findFactoryMethodByParamType(TypeInfo declaringType,
                                                                   TypeName paramType,
                                                                   String methodName) {
        return declaringType.elementInfo()
                .stream()
                // methods
                .filter(ElementInfoPredicates::isMethod)
                // static
                .filter(ElementInfoPredicates::isStatic)
                // @FactoryMethod
                .filter(ElementInfoPredicates.hasAnnotation(PROTOTYPE_FACTORY_METHOD))
                // createMyProperty
                .filter(elementName(methodName))
                // must have a single parameter of the correct type
                .filter(ElementInfoPredicates.hasParams(paramType))
                .map(TypedElementInfo::typeName)
                .findFirst();
    }

    /**
     * Find a method matching the filters from {@link TypeInfo#elementInfo()}.
     *
     * @param signatureFilter expected signature
     * @param typeInfo        type info to search
     * @param predicates      predicates to test against
     * @return found method, ord empty if method does not exist, if more than one exist, the first one is returned
     */
    @SafeVarargs
    private static Optional<TypedElementInfo> findMethod(MethodSignature signatureFilter,
                                                         TypeInfo typeInfo,
                                                         Predicate<TypedElementInfo>... predicates) {
        return typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> {
                    for (Predicate<TypedElementInfo> predicate : predicates) {
                        boolean res = predicate.test(it);
                        if (!res) {
                            return res;
                        }
                    }
                    return true;
                })
                .filter(it -> {
                    if (signatureFilter.returnType() != null) {
                        if (!it.typeName().equals(signatureFilter.returnType())) {
                            return false;
                        }
                    }
                    if (signatureFilter.name() != null) {
                        if (!it.elementName().equals(signatureFilter.name())) {
                            return false;
                        }
                    }
                    List<TypeName> expectedArguments = signatureFilter.arguments();
                    if (expectedArguments != null) {
                        List<TypedElementInfo> actualArguments = it.parameterArguments();
                        if (actualArguments.size() != expectedArguments.size()) {
                            return false;
                        }
                        for (int i = 0; i < expectedArguments.size(); i++) {
                            TypeName expected = expectedArguments.get(i);
                            TypeName actualArgument = actualArguments.get(i).typeName();
                            if (!expected.equals(actualArgument)) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .findFirst();

    }

    record FactoryMethod(TypeName typeWithFactoryMethod,
                         TypeName factoryMethodReturnType,
                         String createMethodName,
                         TypeName argumentType) {
    }

}
