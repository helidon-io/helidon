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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
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
                                            TypeName generator,
                                            List<RegistryCodegenExtensionProvider> extensions) {
        this.ctx = RegistryCodegenContext.create(ctx);
        this.module = ctx.moduleName().orElse(null);
        this.extensions = extensions.stream()
                .map(it -> {
                    RegistryCodegenExtension extension = it.create(this.ctx);
                    return new ExtensionInfo(extension,
                                             discoveryPredicate(it.supportedAnnotations(),
                                                                it.supportedAnnotationPackages()),
                                             it.supportedMetaAnnotations());
                })
                .toList();
    }

    static ServiceRegistryCodegenExtension create(CodegenContext ctx,
                                                  TypeName generator,
                                                  List<RegistryCodegenExtensionProvider> extensions) {
        return new ServiceRegistryCodegenExtension(ctx, generator, extensions);
    }

    @Override
    public void process(io.helidon.codegen.RoundContext roundContext) {
        Collection<TypeInfo> allTypes = roundContext.types();
        if (allTypes.isEmpty()) {
            extensions.forEach(it -> it.extension().process(createRoundContext(roundContext, List.of(), it)));
            return;
        }

        // type info list will contain all mapped annotations, so this is the state we can do annotation processing on
        List<TypeInfoAndAnnotations> annotatedTypes = annotatedTypes(allTypes);

        // and now for each extension, we discover types that contain annotations supported by that extension
        // and create a new round context for each extension

        // for each extension, create a RoundContext with just the stuff it wants
        for (var extension : extensions) {
            extension.extension().process(createRoundContext(roundContext, annotatedTypes, extension));
        }

        writeNewTypes(roundContext);

        for (TypeInfo typeInfo : roundContext.annotatedTypes(SERVICE_ANNOTATION_DESCRIPTOR)) {
            // add each declared descriptor in source code
            Annotation descriptorAnnot = typeInfo.annotation(SERVICE_ANNOTATION_DESCRIPTOR);

            double weight = descriptorAnnot.doubleValue("weight").orElse(Weighted.DEFAULT_WEIGHT);
            Set<TypeName> contracts = descriptorAnnot.typeValues("contracts")
                    .map(Set::copyOf)
                    .orElseGet(Set::of);

            String registryType = descriptorAnnot.stringValue("registryType").orElse("core");

            // predefined service descriptor
            generatedServiceDescriptors.add(DescriptorMetadata.create(registryType,
                                                                      typeInfo.typeName(),
                                                                      weight,
                                                                      contracts));
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

        // if there was any type generated, write it out (will not trigger next round)
        writeNewTypes(roundContext);

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

    @SuppressWarnings("removal")
    private void writeNewTypes(RoundContext roundContext) {
        /*
        This is not longer going to write the types, as it is now (correctly) delegated to
        codegen round context
         */
        // generate all code
        var descriptors = ctx.descriptors();
        for (var descriptor : descriptors) {
            ClassCode classCode = descriptor.classCode();
            generatedServiceDescriptors.add(DescriptorMetadata.create(descriptor.registryType(),
                                                                      classCode.newType(),
                                                                      descriptor.weight(),
                                                                      descriptor.contracts()));
            if (roundContext.generatedType(classCode.newType()).isEmpty()) {
                // add the ones added through deprecated methods on context
                roundContext.addGeneratedType(classCode.newType(),
                                              classCode.classModel(),
                                              classCode.mainTrigger(),
                                              classCode.originatingElements());
            }
        }

        for (ClassCode classCode : ctx.types()) {
            // add the ones added through deprecated methods on context
            if (roundContext.generatedType(classCode.newType()).isEmpty()) {
                roundContext.addGeneratedType(classCode.newType(),
                                              classCode.classModel(),
                                              classCode.mainTrigger(),
                                              classCode.originatingElements());
            }
        }

        descriptors.clear();
        ctx.types().clear();
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
                                                    ExtensionInfo extension) {

        Set<TypeName> availableAnnotations = new HashSet<>();
        Map<TypeName, List<TypeInfo>> annotationToTypes = new HashMap<>();
        Map<TypeName, TypeInfo> processedTypes = new HashMap<>();

        for (TypeInfoAndAnnotations annotatedType : annotatedTypes) {
            for (TypeName annotationType : annotatedType.annotations()) {
                // first check if directly supported
                if (extension.supportedAnnotationsPredicate.test(annotationType)
                        || isMetaAnnotated(roundContext, extension, annotationType)) {

                    availableAnnotations.add(annotationType);
                    processedTypes.put(annotatedType.typeInfo().typeName(), annotatedType.typeInfo());
                    annotationToTypes.computeIfAbsent(annotationType, k -> new ArrayList<>())
                            .add(annotatedType.typeInfo());
                    // annotation is meta-annotated with a supported meta-annotation,
                    // or we support the annotation type, or it is prefixed by the package prefix
                }
            }
        }

        Map<TypeName, Set<TypeName>> metaAnnotated = new HashMap<>();
        for (TypeName typeName : extension.supportedMetaAnnotations()) {
            metaAnnotated.put(typeName, Set.copyOf(roundContext.annotatedAnnotations(typeName)));
        }

        return new RoundContextImpl(
                this.ctx,
                roundContext,
                Set.copyOf(availableAnnotations),
                Map.copyOf(annotationToTypes),
                Map.copyOf(metaAnnotated),
                List.copyOf(processedTypes.values()));
    }

    private boolean isMetaAnnotated(RoundContext roundContext, ExtensionInfo extension, TypeName annotationType) {
        for (TypeName typeName : extension.supportedMetaAnnotations()) {
            if (roundContext.annotatedAnnotations(typeName)
                    .contains(annotationType)) {
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
                                 Set<TypeName> supportedMetaAnnotations) {
    }
}
