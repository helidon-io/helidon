/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.service.codegen.ServiceCodegenAnnotations.WILDCARD_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_G_WRAPPER_IP_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_G_WRAPPER_QUALIFIED_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_G_WRAPPER_SERVICES_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_G_WRAPPER_SUPPLIER_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PER_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_QUALIFIER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_INJECTION_POINT_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIED_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_SERVICES_FACTORY;

/**
 * A service (as declared and annotated with a scope by the user).
 * It may be a service provider (if it implements one of the provider interfaces), or it is a contract
 * implementation on its own.
 */
class DescribedService {
    private final DescribedType serviceType;
    private final DescribedType providedType;

    /*
     the following is only relevant on service itself (not on provided type)
     */
    // type of provider (if this is a provider at all)
    private final FactoryType providerType;
    // qualifiers of provided type are inherited from service
    private final Set<Annotation> qualifiers;
    // provided type does not have a descriptor, only service does
    private final TypeName descriptorType;
    // scope of provided type is inherited from the service
    private final TypeName scope;
    // required for descriptor generation
    private final ServiceSuperType superType;
    // in case this service is a qualified provider, we also get the qualifier it handles
    private final TypeName qualifiedProviderQualifier;

    private DescribedService(DescribedType serviceType,
                             DescribedType providedType,
                             ServiceSuperType superType,
                             TypeName scope,
                             TypeName descriptorType,
                             Set<Annotation> qualifiers,
                             FactoryType providerType,
                             TypeName qualifiedProviderQualifier) {

        this.serviceType = serviceType;
        this.providedType = providedType;
        this.superType = superType;
        this.descriptorType = descriptorType;
        this.scope = scope;
        this.qualifiers = Set.copyOf(qualifiers);
        this.providerType = providerType;
        this.qualifiedProviderQualifier = qualifiedProviderQualifier;
    }

    static DescribedService create(RegistryCodegenContext ctx,
                                   RegistryRoundContext roundContext,
                                   InterceptionSupport interception,
                                   TypeInfo serviceInfo,
                                   ServiceSuperType superType,
                                   TypeName scope) {
        TypeName serviceType = serviceInfo.typeName();
        TypeName descriptorType = ctx.descriptorType(serviceType);

        Set<ResolvedType> directContracts = new HashSet<>();
        Set<ResolvedType> providedContracts = new HashSet<>();
        FactoryType providerType = FactoryType.SERVICE;
        TypeName qualifiedProviderQualifier = null;
        TypeInfo providedTypeInfo = null;
        TypeName providedTypeName = null;

        ServiceContracts serviceContracts = roundContext.serviceContracts(serviceInfo);
        if (serviceInfo.kind() == ElementKind.INTERFACE) {
            directContracts.add(ResolvedType.create(serviceInfo.typeName()));
        }

        // now we know which contracts are OK to use, and we can check the service types and real contracts
        // service is a factory only if it implements the interface directly; this is never inherited
        List<TypeInfo> typeInfos = serviceInfo.interfaceTypeInfo();
        Map<TypeName, TypeInfo> implementedInterfaceTypes = new HashMap<>();
        typeInfos.forEach(it -> implementedInterfaceTypes.put(it.typeName(), it));

        /*
        For each service type we support, gather contracts
         */
        var response = serviceContracts.analyseFactory(TypeNames.SUPPLIER);
        if (response.valid()) {
            providerType = FactoryType.SUPPLIER;
            directContracts.add(ResolvedType.create(response.factoryType()));
            providedContracts.addAll(response.providedContracts());
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
            implementedInterfaceTypes.remove(TypeNames.SUPPLIER);
        }
        response = serviceContracts.analyseFactory(SERVICE_SERVICES_FACTORY);
        if (response.valid()) {
            // if this is not a service type, throw
            if (providerType != FactoryType.SERVICE) {
                throw new CodegenException("Service implements more than one provider type: "
                                                   + providerType + ", and services provider.",
                                           serviceInfo.originatingElementValue());
            }
            providerType = FactoryType.SERVICES;
            directContracts.add(ResolvedType.create(response.providedType()));
            providedContracts.addAll(response.providedContracts());
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
            implementedInterfaceTypes.remove(SERVICE_SERVICES_FACTORY);
        }
        response = serviceContracts.analyseFactory(SERVICE_INJECTION_POINT_FACTORY);
        if (response.valid()) {
            // if this is not a service type, throw
            if (providerType != FactoryType.SERVICE) {
                throw new CodegenException("Service implements more than one provider type: "
                                                   + providerType + ", and injection point provider.",
                                           serviceInfo.originatingElementValue());
            }
            providerType = FactoryType.INJECTION_POINT;
            directContracts.add(ResolvedType.create(response.providedType()));
            providedContracts.addAll(response.providedContracts());
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
            implementedInterfaceTypes.remove(SERVICE_INJECTION_POINT_FACTORY);
        }
        response = serviceContracts.analyseFactory(SERVICE_QUALIFIED_FACTORY);
        if (response.valid()) {
            // if this is not a service type, throw
            if (providerType != FactoryType.SERVICE) {
                throw new CodegenException("Service implements more than one provider type: "
                                                   + providerType + ", and qualified provider.",
                                           serviceInfo.originatingElementValue());
            }
            providerType = FactoryType.QUALIFIED;
            directContracts.add(ResolvedType.create(response.providedType()));
            providedContracts.addAll(response.providedContracts());
            qualifiedProviderQualifier = ServiceContracts
                    .requiredTypeArgument(implementedInterfaceTypes.remove(SERVICE_QUALIFIED_FACTORY), 1);
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
            implementedInterfaceTypes.remove(SERVICE_QUALIFIED_FACTORY);
        }

        // add direct contracts
        HashSet<ResolvedType> processedDirectContracts = new HashSet<>();
        implementedInterfaceTypes.forEach((type, typeInfo) -> {
            serviceContracts.addContracts(directContracts,
                                          processedDirectContracts,
                                          typeInfo);
        });
        // add contracts from super type(s)
        serviceInfo.superTypeInfo().ifPresent(it -> serviceContracts.addContracts(directContracts,
                                                                                  processedDirectContracts,
                                                                                  it));

        DescribedType serviceDescriptor;
        DescribedType providedDescriptor;

        if (providerType == FactoryType.SERVICE) {
            var serviceElements = DescribedElements.create(ctx, interception, directContracts, serviceInfo);
            serviceDescriptor = new DescribedType(serviceInfo,
                                                  serviceInfo.typeName(),
                                                  directContracts,
                                                  serviceElements);

            providedDescriptor = null;
        } else {
            var serviceElements = DescribedElements.create(ctx, interception, Set.of(), serviceInfo);
            serviceDescriptor = new DescribedType(serviceInfo,
                                                  serviceInfo.typeName(),
                                                  directContracts,
                                                  serviceElements);
            DescribedElements providedElements = DescribedElements.create(ctx, interception, providedContracts, providedTypeInfo);

            providedDescriptor = new DescribedType(providedTypeInfo,
                                                   providedTypeName,
                                                   providedContracts,
                                                   providedElements);
        }

        return new DescribedService(
                serviceDescriptor,
                providedDescriptor,
                superType,
                scope,
                descriptorType,
                gatherQualifiers(serviceInfo),
                providerType,
                qualifiedProviderQualifier
        );
    }

    @Override
    public String toString() {
        return serviceType.typeName().fqName();
    }

    TypeName interceptionWrapperSuperType() {
        return switch (providerType()) {
            case NONE, SERVICE -> serviceType.typeName();
            case SUPPLIER -> createType(INTERCEPT_G_WRAPPER_SUPPLIER_FACTORY, providedType.typeName());
            case SERVICES -> createType(INTERCEPT_G_WRAPPER_SERVICES_FACTORY, providedType.typeName());
            case INJECTION_POINT -> createType(INTERCEPT_G_WRAPPER_IP_FACTORY, providedType.typeName());
            case QUALIFIED ->
                    createType(INTERCEPT_G_WRAPPER_QUALIFIED_FACTORY, providedType.typeName(), qualifiedProviderQualifier);
        };
    }

    TypeName providerInterface() {
        return switch (providerType()) {
            case NONE, SERVICE -> serviceType.typeName();
            case SUPPLIER -> createType(TypeNames.SUPPLIER, providedType.typeName());
            case SERVICES -> createType(SERVICE_SERVICES_FACTORY, providedType.typeName());
            case INJECTION_POINT -> createType(SERVICE_INJECTION_POINT_FACTORY, providedType.typeName());
            case QUALIFIED -> createType(SERVICE_QUALIFIED_FACTORY, providedType.typeName(), qualifiedProviderQualifier);
        };
    }

    boolean isFactory() {
        return providerType() != FactoryType.SERVICE && providerType() != FactoryType.NONE;
    }

    DescribedType providedDescriptor() {
        return providedType;
    }

    DescribedType serviceDescriptor() {
        return serviceType;
    }

    ServiceSuperType superType() {
        return superType;
    }

    TypeName descriptorType() {
        return descriptorType;
    }

    TypeName scope() {
        return scope;
    }

    Set<Annotation> qualifiers() {
        return Set.copyOf(qualifiers);
    }

    FactoryType providerType() {
        return providerType;
    }

    TypeName qualifiedProviderQualifier() {
        return qualifiedProviderQualifier;
    }

    private static TypeName createType(TypeName... types) {
        TypeName.Builder builder = TypeName.builder()
                .from(types[0]);

        for (int i = 1; i < types.length; i++) {
            builder.addTypeArgument(types[i]);
        }

        return builder.build();
    }

    private static Set<Annotation> gatherQualifiers(TypeInfo serviceTypeInfo) {
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        if (serviceTypeInfo.hasAnnotation(SERVICE_ANNOTATION_PER_INSTANCE)) {
            qualifiers.add(WILDCARD_NAMED);
        }

        for (Annotation annotation : serviceTypeInfo.annotations()) {
            if (annotation.hasMetaAnnotation(SERVICE_ANNOTATION_QUALIFIER)) {
                qualifiers.add(annotation);
            } else if (serviceTypeInfo.hasMetaAnnotation(annotation.typeName(),
                                                         SERVICE_ANNOTATION_QUALIFIER)) {
                // there are two ways to do this, which is not ideal, and in some cases we only have one filled
                // issue on Github: 9719
                qualifiers.add(annotation);
            }
        }
        return qualifiers;
    }
}
