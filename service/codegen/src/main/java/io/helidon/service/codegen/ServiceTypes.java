/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

final class ServiceTypes {
    private static final List<TypeName> FACTORY_TYPES = List.of(TypeNames.SUPPLIER,
                                                                ServiceCodegenTypes.SERVICE_SERVICES_FACTORY,
                                                                ServiceCodegenTypes.SERVICE_INJECTION_POINT_FACTORY,
                                                                ServiceCodegenTypes.SERVICE_QUALIFIED_FACTORY);

    private ServiceTypes() {
    }

    /**
     * Whether the type is a Helidon service type.
     *
     * @param type type to check
     * @return whether the type is a service
     */
    static boolean isService(TypeInfo type) {
        if (type.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER)) {
            return true;
        }
        if (type.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_PER_INSTANCE)) {
            return true;
        }
        if (type.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_SCOPE)) {
            return true;
        }
        if (type.elementInfo()
                .stream()
                .anyMatch(ElementInfoPredicates.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))) {
            return true;
        }
        for (Annotation annotation : type.annotations()) {
            if (annotation.hasMetaAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER)) {
                return true;
            }
            if (annotation.hasMetaAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_SCOPE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Analyze all supported service factory contracts.
     *
     * @param serviceContracts service contracts
     * @param serviceInfo      service type
     * @return full factory analysis
     */
    static FactoryInfo factoryInfo(ServiceContracts serviceContracts, TypeInfo serviceInfo) {
        List<TypeInfo> typeInfos = serviceInfo.interfaceTypeInfo();
        Map<TypeName, TypeInfo> implementedInterfaceTypes = new HashMap<>();
        typeInfos.forEach(it -> implementedInterfaceTypes.put(it.typeName(), it));

        Set<ResolvedType> directContracts = new HashSet<>();
        Set<ResolvedType> providedContracts = new HashSet<>();
        FactoryType providerType = FactoryType.SERVICE;
        TypeName factoryTypeName = null;
        TypeName qualifiedProviderQualifier = null;
        TypeInfo providedTypeInfo = null;
        TypeName providedTypeName = null;

        for (TypeName factoryType : FACTORY_TYPES) {
            var response = serviceContracts.analyseFactory(factoryType);
            if (!response.valid()) {
                continue;
            }

            if (factoryType.equals(TypeNames.SUPPLIER)) {
                if (providerType != FactoryType.SERVICE) {
                    throw new CodegenException("Service implements more than one provider type: "
                                                       + providerType + ", and supplier.",
                                               serviceInfo.originatingElementValue());
                }
                providerType = FactoryType.SUPPLIER;
                factoryTypeName = response.factoryType();
                directContracts.add(ResolvedType.create(response.factoryType()));
                providedContracts.addAll(response.providedContracts());
                providedTypeName = response.providedType();
                providedTypeInfo = response.providedTypeInfo();
                implementedInterfaceTypes.remove(TypeNames.SUPPLIER);
            } else if (factoryType.equals(ServiceCodegenTypes.SERVICE_SERVICES_FACTORY)) {
                if (providerType != FactoryType.SERVICE) {
                    throw new CodegenException("Service implements more than one provider type: "
                                                       + providerType + ", and services provider.",
                                               serviceInfo.originatingElementValue());
                }
                providerType = FactoryType.SERVICES;
                factoryTypeName = response.factoryType();
                directContracts.add(ResolvedType.create(response.providedType()));
                providedContracts.addAll(response.providedContracts());
                providedTypeName = response.providedType();
                providedTypeInfo = response.providedTypeInfo();
                implementedInterfaceTypes.remove(ServiceCodegenTypes.SERVICE_SERVICES_FACTORY);
            } else if (factoryType.equals(ServiceCodegenTypes.SERVICE_INJECTION_POINT_FACTORY)) {
                if (providerType != FactoryType.SERVICE) {
                    throw new CodegenException("Service implements more than one provider type: "
                                                       + providerType + ", and injection point provider.",
                                               serviceInfo.originatingElementValue());
                }
                providerType = FactoryType.INJECTION_POINT;
                factoryTypeName = response.factoryType();
                directContracts.add(ResolvedType.create(response.providedType()));
                providedContracts.addAll(response.providedContracts());
                providedTypeName = response.providedType();
                providedTypeInfo = response.providedTypeInfo();
                implementedInterfaceTypes.remove(ServiceCodegenTypes.SERVICE_INJECTION_POINT_FACTORY);
            } else if (factoryType.equals(ServiceCodegenTypes.SERVICE_QUALIFIED_FACTORY)) {
                if (providerType != FactoryType.SERVICE) {
                    throw new CodegenException("Service implements more than one provider type: "
                                                       + providerType + ", and qualified provider.",
                                               serviceInfo.originatingElementValue());
                }
                providerType = FactoryType.QUALIFIED;
                factoryTypeName = response.factoryType();
                directContracts.add(ResolvedType.create(response.providedType()));
                providedContracts.addAll(response.providedContracts());
                qualifiedProviderQualifier = ServiceContracts
                        .requiredTypeArgument(implementedInterfaceTypes.remove(ServiceCodegenTypes.SERVICE_QUALIFIED_FACTORY),
                                              1);
                providedTypeName = response.providedType();
                providedTypeInfo = response.providedTypeInfo();
            }
        }

        return new FactoryInfo(providerType,
                               factoryTypeName,
                               providedTypeName,
                               providedTypeInfo,
                               qualifiedProviderQualifier,
                               Set.copyOf(directContracts),
                               Set.copyOf(providedContracts),
                               Map.copyOf(implementedInterfaceTypes));
    }

    /**
     * Contracts provided by the service factory interface implemented by a service.
     *
     * @param serviceContracts service contracts
     * @param serviceInfo      service type
     * @return provided contracts
     */
    static Set<ResolvedType> factoryProvidedContracts(ServiceContracts serviceContracts, TypeInfo serviceInfo) {
        return factoryInfo(serviceContracts, serviceInfo).providedContracts();
    }

    record FactoryInfo(FactoryType providerType,
                       TypeName factoryTypeName,
                       TypeName providedTypeName,
                       TypeInfo providedTypeInfo,
                       TypeName qualifiedProviderQualifier,
                       Set<ResolvedType> directContracts,
                       Set<ResolvedType> providedContracts,
                       Map<TypeName, TypeInfo> remainingImplementedInterfaces) {
    }
}
