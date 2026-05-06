/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;
import io.helidon.service.metadata.DescriptorMetadata;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_DESCRIPTOR;

/**
 * Handles processing of all extensions, creates context and writes types.
 */
class ServiceRegistryCodegenExtension implements CodegenExtension {
    private final Set<DescriptorMetadata> generatedServiceDescriptors = new HashSet<>();
    private final List<ExtensionInfo> extensions;
    private final RegistryCodegenContext ctx;
    private final String module;

    private ServiceRegistryCodegenExtension(CodegenContext ctx,
                                            List<RegistryCodegenExtensionProvider> extensions) {
        this.ctx = RegistryCodegenContext.create(ctx);
        this.module = ctx.moduleName().orElse(null);
        this.extensions = extensions.stream()
                .map(it -> {
                    RegistryCodegenExtension extension = it.create(this.ctx);
                    return new ExtensionInfo(extension,
                                             discoveryPredicate(it.supportedAnnotations(),
                                                                it.supportedAnnotationPackages()),
                                             it.supportedMetaAnnotations(),
                                             it.supportsServiceContractAnnotations());
                })
                .toList();
    }

    static ServiceRegistryCodegenExtension create(CodegenContext ctx,
                                                  List<RegistryCodegenExtensionProvider> extensions) {
        return new ServiceRegistryCodegenExtension(ctx, extensions);
    }

    @Override
    public void process(io.helidon.codegen.RoundContext roundContext) {
        List<DescriptorClassCode> descriptors = new ArrayList<>();
        Collection<TypeInfo> allTypes = roundContext.types();
        if (allTypes.isEmpty()) {
            extensions.forEach(it -> it.extension()
                    .process(createRoundContext(
                            roundContext,
                            List.of(),
                            it,
                            descriptors)));
            return;
        }

        // type info list will contain all mapped annotations, so this is the state we can do annotation processing on
        List<TypeInfoAndAnnotations> annotatedTypes = annotatedTypes(allTypes);

        // and now for each extension, we discover types that contain annotations supported by that extension
        // and create a new round context for each extension

        // for each extension, create a RoundContext with just the stuff it wants
        for (var extension : extensions) {
            extension.extension().process(createRoundContext(roundContext, annotatedTypes, extension, descriptors));
        }

        writeNewTypes(descriptors);

        for (TypeInfo typeInfo : roundContext.annotatedTypes(SERVICE_ANNOTATION_DESCRIPTOR)) {
            // add each declared descriptor in source code
            Annotation descriptorAnnot = typeInfo.annotation(SERVICE_ANNOTATION_DESCRIPTOR);

            double weight = descriptorAnnot.doubleValue("weight").orElse(Weighted.DEFAULT_WEIGHT);
            Set<ResolvedType> contracts = descriptorAnnot.typeValues("contracts")
                    .stream()
                    .flatMap(List::stream)
                    .map(ResolvedType::create)
                    .collect(Collectors.toUnmodifiableSet());

            // predefined service descriptor
            generatedServiceDescriptors.add(DescriptorMetadata.create(typeInfo.typeName(),
                                                                      weight,
                                                                      contracts,
                                                                      Set.of()));
        }

        if (roundContext.availableAnnotations().size() == 1 && roundContext.availableAnnotations()
                .contains(TypeNames.GENERATED)) {

            // no other types generated by Helidon annotation processors, we can generate module component (unless already done)
            if (!generatedServiceDescriptors.isEmpty()) {
                addDescriptorsToServiceMeta();
                generatedServiceDescriptors.clear();
            }
        }
    }

    @Override
    public void processingOver(io.helidon.codegen.RoundContext roundContext) {
        // do processing over in each extension
        extensions
                .stream()
                .map(ExtensionInfo::extension)
                .forEach(RegistryCodegenExtension::processingOver);

        if (!generatedServiceDescriptors.isEmpty()) {
            // re-check, maybe we run from a tool that does not generate anything except for the module component,
            // so let's create it now anyway (if created above, the set of descriptors is empty, so it is not attempted again
            // if somebody adds a service descriptor when processingOver, than it is wrong anyway
            addDescriptorsToServiceMeta();
            generatedServiceDescriptors.clear();
        }
    }

    private static Predicate<TypeName> discoveryPredicate(Set<TypeName> typeNames, Collection<String> packages) {
        List<String> prefixes = packages.stream()
                .map(it -> it.endsWith(".*") ? it.substring(0, it.length() - 2) : it)
                .toList();
        return typeName -> {
            if (typeNames.contains(typeName)) {
                return true;
            }
            String packageName = typeName.packageName();
            for (String prefix : prefixes) {
                if (packageName.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        };
    }

    private void addDescriptorsToServiceMeta() {
        // and write the module component
        Optional<ModuleInfo> currentModule = ctx.module();

        // generate module
        String moduleName = this.module == null ? currentModule.map(ModuleInfo::name).orElse(null) : module;
        String packageName = CodegenOptions.CODEGEN_PACKAGE.findValue(ctx.options())
                .orElseGet(() -> topLevelPackage(generatedServiceDescriptors));
        boolean hasModule = moduleName != null && !"unnamed module".equals(moduleName);
        if (!hasModule) {
            moduleName = "unnamed/" + packageName + (ctx.scope().isProduction() ? "" : "/" + ctx.scope().name());
        }
        HelidonMetaInfServices services = HelidonMetaInfServices.create(ctx.filer(),
                                                                        moduleName);

        services.addAll(generatedServiceDescriptors);
        services.write();
    }

    private void writeNewTypes(List<DescriptorClassCode> descriptors) {
        /*
        This is no longer going to write the types, as it is now (correctly) delegated to
        codegen round context
         */
        // generate all code
        for (var descriptor : descriptors) {
            ClassCode classCode = descriptor.classCode();
            generatedServiceDescriptors.add(DescriptorMetadata.create(classCode.newType(),
                                                                      descriptor.weight(),
                                                                      descriptor.contracts(),
                                                                      descriptor.factoryContracts()));
        }
    }

    private List<TypeInfoAndAnnotations> annotatedTypes(Collection<TypeInfo> allTypes) {
        List<TypeInfoAndAnnotations> result = new ArrayList<>();

        for (TypeInfo typeInfo : allTypes) {
            result.add(new TypeInfoAndAnnotations(typeInfo, TypeHierarchy.nestedAnnotations(ctx, typeInfo)));
        }
        return result;
    }

    private RegistryRoundContext createRoundContext(RoundContext roundContext,
                                                    List<TypeInfoAndAnnotations> annotatedTypes,
                                                    ExtensionInfo extension,
                                                    List<DescriptorClassCode> newDescriptors) {

        Set<TypeName> availableAnnotations = new HashSet<>();
        Map<TypeName, List<TypeInfo>> annotationToTypes = new HashMap<>();
        Map<TypeName, Set<TypeName>> supportedAnnotationsCache = new HashMap<>();
        Map<TypeName, Set<TypeName>> metaAnnotationsCache = new HashMap<>();
        Map<TypeName, Boolean> metaAnnotatedCache = new HashMap<>();
        Map<TypeName, Set<TypeName>> metaAnnotated = new HashMap<>();
        Map<TypeName, TypeInfo> processedTypes = new HashMap<>();

        for (TypeName typeName : extension.supportedMetaAnnotations()) {
            metaAnnotated.put(typeName, new HashSet<>(roundContext.annotatedAnnotations(typeName)));
        }

        for (TypeInfoAndAnnotations annotatedType : annotatedTypes) {
            for (TypeName annotationType : annotatedType.annotations()) {
                // first check if directly supported
                if (extension.supportedAnnotationsPredicate.test(annotationType)
                        || isMetaAnnotated(roundContext, extension, annotationType, metaAnnotationsCache, metaAnnotatedCache)) {

                    availableAnnotations.add(annotationType);
                    addMetaAnnotated(metaAnnotated,
                                     annotationType,
                                     metaAnnotations(roundContext, extension, annotationType, metaAnnotationsCache));
                    processedTypes.put(annotatedType.typeInfo().typeName(), annotatedType.typeInfo());
                    annotationToTypes.computeIfAbsent(annotationType, k -> new ArrayList<>())
                            .add(annotatedType.typeInfo());
                    // annotation is meta-annotated with a supported meta-annotation,
                    // or we support the annotation type, or it is prefixed by the package prefix
                }
            }
            Set<TypeName> contractAnnotations = supportedServiceContractAnnotations(roundContext,
                                                                                    extension,
                                                                                    annotatedType.typeInfo(),
                                                                                    supportedAnnotationsCache,
                                                                                    metaAnnotationsCache,
                                                                                    metaAnnotatedCache);
            if (!contractAnnotations.isEmpty()) {
                processedTypes.put(annotatedType.typeInfo().typeName(),
                                   effectiveServiceType(roundContext, annotatedType.typeInfo()));
                availableAnnotations.addAll(contractAnnotations);
                contractAnnotations.forEach(it -> addMetaAnnotated(metaAnnotated,
                                                                    it,
                                                                    metaAnnotations(roundContext,
                                                                                    extension,
                                                                                    it,
                                                                                    metaAnnotationsCache)));
            }
        }

        Map<TypeName, Set<TypeName>> metaAnnotatedCopy = new HashMap<>();
        metaAnnotated.forEach((key, value) -> metaAnnotatedCopy.put(key, Set.copyOf(value)));

        return new RoundContextImpl(
                ctx,
                roundContext,
                newDescriptors::add,
                Set.copyOf(availableAnnotations),
                Map.copyOf(annotationToTypes),
                Map.copyOf(metaAnnotatedCopy),
                List.copyOf(processedTypes.values()));
    }

    private TypeInfo effectiveServiceType(RoundContext roundContext, TypeInfo serviceType) {
        ServiceContracts serviceContracts = ServiceContracts.create(ctx.options(), roundContext::typeInfo, serviceType);
        Set<ResolvedType> contracts = new HashSet<>();
        serviceContracts.addContracts(contracts, new HashSet<>(), serviceType);

        List<TypedElementInfo> elements = new ArrayList<>();
        Set<ElementSignature> signatures = new HashSet<>();
        addEffectiveElements(elements, signatures, TypedElements.gatherElements(ctx, contracts, serviceType));

        ServiceTypes.FactoryInfo factoryInfo = ServiceTypes.factoryInfo(serviceContracts, serviceType);
        if (factoryInfo.providerType() != FactoryType.SERVICE && factoryInfo.providedTypeInfo() != null) {
            addEffectiveMethodElements(elements,
                                       signatures,
                                       TypedElements.gatherElements(ctx,
                                                                    factoryInfo.providedContracts(),
                                                                    factoryInfo.providedTypeInfo()));
        }

        return TypeInfo.builder(serviceType)
                .elementInfo(elements)
                .build();
    }

    private void addEffectiveElements(List<TypedElementInfo> elements,
                                      Set<ElementSignature> signatures,
                                      List<TypedElements.ElementMeta> elementMetas) {
        for (TypedElements.ElementMeta elementMeta : elementMetas) {
            TypedElementInfo element = elementMeta.effectiveElement();
            if (signatures.add(element.signature())) {
                elements.add(element);
            }
        }
    }

    private void addEffectiveMethodElements(List<TypedElementInfo> elements,
                                            Set<ElementSignature> signatures,
                                            List<TypedElements.ElementMeta> elementMetas) {
        for (TypedElements.ElementMeta elementMeta : elementMetas) {
            TypedElementInfo element = elementMeta.effectiveElement();
            if (element.kind() == ElementKind.METHOD && signatures.add(element.signature())) {
                elements.add(element);
            }
        }
    }

    private Set<TypeName> supportedServiceContractAnnotations(RoundContext roundContext,
                                                              ExtensionInfo extension,
                                                              TypeInfo serviceType,
                                                              Map<TypeName, Set<TypeName>> supportedAnnotationsCache,
                                                              Map<TypeName, Set<TypeName>> metaAnnotationsCache,
                                                              Map<TypeName, Boolean> metaAnnotatedCache) {
        if (!extension.supportsServiceContractAnnotations() || !ServiceTypes.isService(serviceType)) {
            return Set.of();
        }

        ServiceContracts serviceContracts = ServiceContracts.create(ctx.options(), roundContext::typeInfo, serviceType);
        Set<ResolvedType> contracts = new HashSet<>();
        serviceContracts.addContracts(contracts, new HashSet<>(), serviceType);

        Set<TypeName> result = new HashSet<>(supportedAnnotationsOnContracts(roundContext,
                                                                             extension,
                                                                             contracts,
                                                                             supportedAnnotationsCache,
                                                                             metaAnnotationsCache,
                                                                             metaAnnotatedCache));
        result.addAll(supportedAnnotationsOnContracts(roundContext,
                                                      extension,
                                                      ServiceTypes.factoryProvidedContracts(serviceContracts, serviceType),
                                                      supportedAnnotationsCache,
                                                      metaAnnotationsCache,
                                                      metaAnnotatedCache));

        return Set.copyOf(result);
    }

    private Set<TypeName> supportedAnnotationsOnContracts(RoundContext roundContext,
                                                          ExtensionInfo extension,
                                                          Set<ResolvedType> contracts,
                                                          Map<TypeName, Set<TypeName>> supportedAnnotationsCache,
                                                          Map<TypeName, Set<TypeName>> metaAnnotationsCache,
                                                          Map<TypeName, Boolean> metaAnnotatedCache) {
        Set<TypeName> result = new HashSet<>();
        for (ResolvedType contract : contracts) {
            TypeName contractType = contract.type();
            result.addAll(supportedAnnotationsCache.computeIfAbsent(contractType,
                                                                    it -> supportedAnnotations(roundContext,
                                                                                               extension,
                                                                                               contractType,
                                                                                               metaAnnotationsCache,
                                                                                               metaAnnotatedCache)));
        }

        return result;
    }

    private Set<TypeName> supportedAnnotations(RoundContext roundContext,
                                               ExtensionInfo extension,
                                               TypeName typeName,
                                               Map<TypeName, Set<TypeName>> metaAnnotationsCache,
                                               Map<TypeName, Boolean> metaAnnotatedCache) {
        Optional<TypeInfo> typeInfo = roundContext.typeInfo(typeName)
                .or(() -> roundContext.typeInfo(typeName.genericTypeName()));
        return typeInfo.map(it -> supportedAnnotations(roundContext,
                                                       extension,
                                                       it,
                                                       metaAnnotationsCache,
                                                       metaAnnotatedCache))
                .orElseGet(Set::of);
    }

    private Set<TypeName> supportedAnnotations(RoundContext roundContext,
                                               ExtensionInfo extension,
                                               TypeInfo typeInfo,
                                               Map<TypeName, Set<TypeName>> metaAnnotationsCache,
                                               Map<TypeName, Boolean> metaAnnotatedCache) {
        Set<TypeName> result = new HashSet<>();
        for (TypeName annotationType : TypeHierarchy.nestedAnnotations(ctx, typeInfo)) {
            if (extension.supportedAnnotationsPredicate.test(annotationType)
                    || isMetaAnnotated(roundContext,
                                       extension,
                                       annotationType,
                                       metaAnnotationsCache,
                                       metaAnnotatedCache)) {
                result.add(annotationType);
            }
        }

        return result;
    }

    private boolean isMetaAnnotated(RoundContext roundContext,
                                    ExtensionInfo extension,
                                    TypeName annotationType,
                                    Map<TypeName, Set<TypeName>> metaAnnotationsCache,
                                    Map<TypeName, Boolean> metaAnnotatedCache) {
        return metaAnnotatedCache.computeIfAbsent(annotationType,
                                                  it -> !metaAnnotations(roundContext,
                                                                         extension,
                                                                         annotationType,
                                                                         metaAnnotationsCache).isEmpty());
    }

    private Set<TypeName> metaAnnotations(RoundContext roundContext,
                                          ExtensionInfo extension,
                                          TypeName annotationType,
                                          Map<TypeName, Set<TypeName>> metaAnnotationsCache) {
        return metaAnnotationsCache.computeIfAbsent(annotationType,
                                                    it -> metaAnnotations(roundContext, extension, annotationType));
    }

    private Set<TypeName> metaAnnotations(RoundContext roundContext, ExtensionInfo extension, TypeName annotationType) {
        Set<TypeName> result = new HashSet<>();
        for (TypeName typeName : extension.supportedMetaAnnotations()) {
            if (roundContext.annotatedAnnotations(typeName).contains(annotationType)) {
                result.add(typeName);
            }
        }
        Optional<TypeInfo> annotationInfo = ctx.typeInfo(annotationType);
        if (annotationInfo.isEmpty()) {
            return Set.copyOf(result);
        }
        for (TypeName supportedMetaAnnotation : extension.supportedMetaAnnotations()) {
            if (!result.contains(supportedMetaAnnotation)
                    && isMetaAnnotated(annotationInfo.get(), Set.of(supportedMetaAnnotation), new HashSet<>())) {
                result.add(supportedMetaAnnotation);
            }
        }

        return Set.copyOf(result);
    }

    private void addMetaAnnotated(Map<TypeName, Set<TypeName>> metaAnnotated,
                                  TypeName annotationType,
                                  Set<TypeName> metaAnnotations) {
        for (TypeName metaAnnotation : metaAnnotations) {
            metaAnnotated.computeIfAbsent(metaAnnotation, ignored -> new HashSet<>())
                    .add(annotationType);
        }
    }

    private boolean isMetaAnnotated(TypeInfo annotationType, Set<TypeName> supportedMetaAnnotations, Set<TypeName> processed) {
        if (!processed.add(annotationType.typeName())) {
            return false;
        }

        for (Annotation annotation : annotationType.annotations()) {
            if (supportedMetaAnnotations.contains(annotation.typeName())) {
                return true;
            }
            for (TypeName supportedMetaAnnotation : supportedMetaAnnotations) {
                if (annotation.hasMetaAnnotation(supportedMetaAnnotation)) {
                    return true;
                }
            }
            if (ctx.typeInfo(annotation.typeName())
                    .map(it -> isMetaAnnotated(it, supportedMetaAnnotations, processed))
                    .orElse(false)) {
                return true;
            }
        }

        return false;
    }

    private String topLevelPackage(Set<DescriptorMetadata> typeNames) {
        String thePackage = typeNames.iterator().next().descriptorType().packageName();

        for (DescriptorMetadata typeName : typeNames) {
            String nextPackage = typeName.descriptorType().packageName();
            if (nextPackage.length() < thePackage.length()) {
                thePackage = nextPackage;
            }
        }

        return thePackage;
    }

    private record TypeInfoAndAnnotations(TypeInfo typeInfo, Set<TypeName> annotations) {
    }

    private record ExtensionInfo(RegistryCodegenExtension extension,
                                 Predicate<TypeName> supportedAnnotationsPredicate,
                                 Set<TypeName> supportedMetaAnnotations,
                                 boolean supportsServiceContractAnnotations) {
    }
}
