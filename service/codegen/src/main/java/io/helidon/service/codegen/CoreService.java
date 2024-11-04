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

package io.helidon.service.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER;
import static java.util.function.Predicate.not;

/**
 * A service (as declared and annotated by @Service.Provider).
 * <p>
 * A service may be a {@link java.util.function.Supplier} of instance, or direct contract implementation.
 */
class CoreService {
    private static final TypedElementInfo DEFAULT_CONSTRUCTOR = TypedElementInfo.builder()
            .typeName(TypeNames.OBJECT)
            .accessModifier(AccessModifier.PUBLIC)
            .kind(ElementKind.CONSTRUCTOR)
            .build();

    // whether this is an abstract class or not
    private final boolean isAbstract;
    // If this is a factory or not
    private final CoreFactoryType factoryType;
    // The class name of the service
    private final TypeName serviceType;
    // The class name of the service descriptor to be generated
    private final TypeName descriptorType;
    // If this service extends another service
    private final ServiceSuperType superType;
    // Required constructor "injection points" of this service
    private final List<CoreDependency> dependencies;
    private final CoreTypeConstants constants;
    // Contracts provided by this service
    private final Set<ResolvedType> contracts;
    private final Set<ResolvedType> factoryContracts;

    CoreService(boolean isAbstract,
                CoreFactoryType factoryType,
                TypeName serviceType,
                TypeName descriptorType,
                ServiceSuperType superType,
                List<CoreDependency> dependencies,
                CoreTypeConstants constants,
                Set<ResolvedType> contracts,
                Set<ResolvedType> factoryContracts) {
        this.isAbstract = isAbstract;
        this.factoryType = factoryType;
        this.serviceType = serviceType;
        this.descriptorType = descriptorType;
        this.superType = superType;
        this.dependencies = dependencies;
        this.constants = constants;
        this.contracts = contracts;
        this.factoryContracts = factoryContracts;
    }

    static CoreService create(RegistryCodegenContext ctx,
                              RegistryRoundContext roundContext,
                              TypeInfo serviceInfo,
                              Collection<TypeInfo> allServices) {

        TypeName serviceType = serviceInfo.typeName();
        TypeName descriptorType = ctx.descriptorType(serviceType);

        Set<ResolvedType> directContracts = new HashSet<>();
        Set<ResolvedType> providedContracts = new HashSet<>();
        CoreFactoryType factoryType = CoreFactoryType.SERVICE;

        ServiceContracts serviceContracts = roundContext.serviceContracts(serviceInfo);

        // now we know which contracts are OK to use, and we can check the service types and real contracts
        // service is a factory only if it implements the interface directly; this is never inherited
        List<TypeInfo> typeInfos = serviceInfo.interfaceTypeInfo();
        Map<TypeName, TypeInfo> implementedInterfaceTypes = new HashMap<>();
        typeInfos.forEach(it -> implementedInterfaceTypes.put(it.typeName(), it));

        var response = serviceContracts.analyseFactory(TypeNames.SUPPLIER);
        if (response.valid()) {
            factoryType = CoreFactoryType.SUPPLIER;
            directContracts.add(ResolvedType.create(response.factoryType()));
            providedContracts.addAll(response.providedContracts());
            implementedInterfaceTypes.remove(TypeNames.SUPPLIER);
        }

        // add direct contracts
        HashSet<ResolvedType> processedDirectContracts = new HashSet<>();
        implementedInterfaceTypes.forEach((type, typeInfo) -> {
            serviceContracts.addContracts(directContracts,
                                          processedDirectContracts,
                                          typeInfo);
        });

        // if we are a factory, our direct contracts are a different set (as it is satisfied by the instance directly
        // and not by the factory method)
        Set<ResolvedType> factoryContracts = (factoryType == CoreFactoryType.SUPPLIER)
                ? directContracts
                : Set.of();
        // and provided contracts are the "real" contracts of the provider
        Set<ResolvedType> contracts = (factoryType == CoreFactoryType.SUPPLIER)
                ? providedContracts
                : directContracts;

        DependencyResult dependencyResult = gatherDependencies(ctx, serviceInfo);
        ServiceSuperType superType = superType(ctx, serviceInfo, allServices);

        // the service metadata must contain all contracts the service provides (both provider and provided)
        return new CoreService(isAbstract(serviceInfo),
                               factoryType,
                               serviceType,
                               descriptorType,
                               superType,
                               dependencyResult.result(),
                               dependencyResult.constants(),
                               contracts,
                               factoryContracts);
    }

    boolean isAbstract() {
        return isAbstract;
    }

    CoreFactoryType factoryType() {
        return factoryType;
    }

    TypeName serviceType() {
        return serviceType;
    }

    TypeName descriptorType() {
        return descriptorType;
    }

    ServiceSuperType superType() {
        return superType;
    }

    List<CoreDependency> dependencies() {
        return dependencies;
    }

    Set<ResolvedType> contracts() {
        return contracts;
    }

    Set<ResolvedType> factoryContracts() {
        return factoryContracts;
    }

    CoreTypeConstants constants() {
        return constants;
    }

    // find super type if it is also a service (or has a service descriptor)
    private static ServiceSuperType superType(RegistryCodegenContext ctx, TypeInfo serviceInfo, Collection<TypeInfo> services) {
        Optional<TypeInfo> maybeSuperType = serviceInfo.superTypeInfo();
        if (maybeSuperType.isEmpty()) {
            // this class does not have a super type
            return ServiceSuperType.create();
        }

        // check if the super type is part of current annotation processing
        TypeInfo superType = maybeSuperType.get();
        TypeName expectedSuperDescriptor = ctx.descriptorType(superType.typeName());
        TypeName superTypeToExtend = TypeName.builder(expectedSuperDescriptor)
                .addTypeArgument(TypeName.create("T"))
                .build();
        boolean isCore = superType.hasAnnotation(SERVICE_ANNOTATION_PROVIDER);
        if (!isCore) {
            throw new CodegenException("Service annotated with @Service.Provider extends invalid supertype,"
                                               + " the super type must also be a @Service.Provider. Type: "
                                               + serviceInfo.typeName().fqName() + ", super type: "
                                               + superType.typeName().fqName(),
                                       serviceInfo.originatingElementValue());
        }

        for (TypeInfo service : services) {
            if (service.typeName().equals(superType.typeName())) {
                return ServiceSuperType.create(service, "core", superTypeToExtend);
            }
        }
        // if not found in current list, try checking existing types
        return ctx.typeInfo(expectedSuperDescriptor)
                .map(it -> ServiceSuperType.create(superType, "core", superTypeToExtend))
                .orElseGet(ServiceSuperType::create);
    }

    private static DependencyResult gatherDependencies(RegistryCodegenContext ctx, TypeInfo serviceInfo) {
        TypedElementInfo constructor = constructor(serviceInfo);

        // core services only support inversion of control for constructor parameters
        AtomicInteger dependencyIndex = new AtomicInteger();

        List<CoreDependency> result = new ArrayList<>();
        CoreTypeConstants constants = new CoreTypeConstants();

        for (TypedElementInfo param : constructor.parameterArguments()) {
            result.add(CoreDependency.create(ctx,
                                             constructor,
                                             param,
                                             constants,
                                             dependencyIndex.getAndIncrement()));
        }

        return new DependencyResult(result, constants);
    }

    private static TypedElementInfo constructor(TypeInfo serviceInfo) {
        var allConstructors = serviceInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isConstructor)
                .collect(Collectors.toUnmodifiableList());

        if (allConstructors.isEmpty()) {
            // no constructor, use default
            return DEFAULT_CONSTRUCTOR;
        }

        var nonPrivateConstructors = allConstructors.stream()
                .filter(not(ElementInfoPredicates::isPrivate))
                .collect(Collectors.toUnmodifiableList());

        if (nonPrivateConstructors.isEmpty()) {
            throw new CodegenException("Service does not contain any non-private constructor",
                                       serviceInfo.originatingElementValue());
        }
        if (allConstructors.size() > 1) {
            throw new CodegenException("Service contains more than one non-private constructor",
                                       serviceInfo.originatingElementValue());
        }

        return allConstructors.getFirst();
    }

    private static boolean isAbstract(TypeInfo serviceInfo) {
        return serviceInfo.elementModifiers().contains(Modifier.ABSTRACT)
                && serviceInfo.kind() == ElementKind.CLASS;
    }

    private record DependencyResult(List<CoreDependency> result, CoreTypeConstants constants) {
    }
}
