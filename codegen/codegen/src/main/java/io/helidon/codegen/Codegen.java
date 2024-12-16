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

package io.helidon.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Central piece of code processing and generation.
 * This type loads {@link io.helidon.codegen.spi.CodegenExtensionProvider extension providers}, and invokes
 * each {@link io.helidon.codegen.spi.CodegenExtension} with appropriate types and annotations.
 */
public class Codegen {
    private static final List<CodegenExtensionProvider> EXTENSIONS =
            HelidonServiceLoader.create(ServiceLoader.load(CodegenExtensionProvider.class,
                                                           Codegen.class.getClassLoader()))
                    .asList();
    private static final Set<Option<?>> SUPPORTED_APT_OPTIONS;

    static {
        Set<Option<?>> supportedOptions = EXTENSIONS.stream()
                .flatMap(it -> it.supportedOptions().stream())
                .collect(Collectors.toSet());
        supportedOptions.add(CodegenOptions.CODEGEN_SCOPE);
        supportedOptions.add(CodegenOptions.INDENT_TYPE);
        supportedOptions.add(CodegenOptions.INDENT_COUNT);

        SUPPORTED_APT_OPTIONS = Set.copyOf(supportedOptions);
    }

    private final CodegenContext ctx;
    private final List<ExtensionInfo> extensions;
    private final Set<TypeName> supportedAnnotations;
    private final Set<TypeName> supportedMetaAnnotations;
    private final Set<String> supportedPackagePrefixes;

    private Codegen(CodegenContext ctx, TypeName generator) {
        this.ctx = ctx;

        Set<TypeName> supportedAnnotations = new HashSet<>(ctx.mapperSupportedAnnotations());
        Set<TypeName> supportedMetaAnnotations = new HashSet<>();
        Set<String> supportedPackagePrefixes = new HashSet<>();

        this.extensions = EXTENSIONS.stream()
                .map(it -> {
                    CodegenExtension extension = it.create(this.ctx, generator);

                    Set<TypeName> extensionAnnotations = it.supportedAnnotations();
                    Set<String> extensionPackages = it.supportedAnnotationPackages();
                    Set<TypeName> extensionMetaAnnotations = it.supportedMetaAnnotations();

                    supportedAnnotations.addAll(extensionAnnotations);
                    supportedMetaAnnotations.addAll(extensionMetaAnnotations);
                    supportedPackagePrefixes.addAll(extensionPackages);

                    Predicate<TypeName> annotationPredicate = discoveryPredicate(extensionAnnotations,
                                                                                 extensionPackages);

                    return new ExtensionInfo(extension,
                                             annotationPredicate,
                                             extensionMetaAnnotations);
                })
                .toList();

        ctx.mapperSupportedAnnotationPackages()
                .stream()
                .map(Codegen::toPackagePrefix)
                .forEach(supportedPackagePrefixes::add);

        this.supportedAnnotations = Set.copyOf(supportedAnnotations);
        this.supportedPackagePrefixes = Set.copyOf(supportedPackagePrefixes);
        this.supportedMetaAnnotations = Set.copyOf(supportedMetaAnnotations);
    }

    /**
     * Create a new instance of the top level Codegen.
     * This type discovers all {@link io.helidon.codegen.spi.CodegenExtensionProvider CodegenExtensionProviders}
     * and invokes the provided {@link io.helidon.codegen.spi.CodegenExtension CodegenExtensions} as needed.
     *
     * @param ctx       code processing and generation context
     * @param generator type name of the invoking generator (such as maven plugin, annotation procesor, command line tool)
     * @return a new codegen instance
     */
    public static Codegen create(CodegenContext ctx, TypeName generator) {
        Codegen codegen = new Codegen(ctx, generator);
        Set<Option<?>> allOptions = new HashSet<>(SUPPORTED_APT_OPTIONS);
        allOptions.addAll(ctx.supportedOptions());
        ctx.options().validate(allOptions);
        return codegen;
    }

    /**
     * Set of supported options by all extensions.
     *
     * @return supported options
     */
    public static Set<Option<?>> supportedOptions() {
        return SUPPORTED_APT_OPTIONS;
    }

    /**
     * Process all types discovered.
     * This method analyzes the types and invokes each extension with the correct subset.
     *
     * @param allTypes all types for this processing round
     */
    public void process(List<TypeInfo> allTypes) {
        List<ClassCode> toWrite = new ArrayList<>();

        // type info list will contain all mapped annotations, so this is the state we can do annotation processing on
        List<TypeInfoAndAnnotations> annotatedTypes = annotatedTypes(allTypes);

        for (var extension : extensions) {
            // and now for each extension, we discover types that contain annotations supported by that extension
            // and create a new round context
            RoundContextImpl roundCtx = createRoundContext(annotatedTypes, extension, toWrite);
            extension.extension().process(roundCtx);
            toWrite.addAll(roundCtx.newTypes());
        }

        writeNewTypes(toWrite);
    }

    /**
     * Finish processing. No additional rounds will be done.
     */
    public void processingOver() {
        List<ClassCode> toWrite = new ArrayList<>();

        // do processing over in each extension
        for (var extension : extensions) {
            RoundContextImpl roundCtx = createRoundContext(List.of(), extension, toWrite);
            extension.extension().processingOver(roundCtx);
            toWrite.addAll(roundCtx.newTypes());
        }

        // if there was any type generated, write it out (will not trigger next round)
        writeNewTypes(toWrite);
    }

    /**
     * A set of annotation types.
     *
     * @return set of annotations that should be processed
     */
    public Set<TypeName> supportedAnnotations() {
        return supportedAnnotations;
    }

    /**
     * A set of package prefixes (expected to end with a {@code .}).
     *
     * @return set of package prefixes of annotations that should be processed
     */
    public Set<String> supportedAnnotationPackagePrefixes() {
        return supportedPackagePrefixes;
    }

    /**
     * A set of annotation types that may annotate annotation types.
     *
     * @return set of meta annotations for annotations to be processed
     */
    public Set<TypeName> supportedMetaAnnotations() {
        return supportedMetaAnnotations;
    }

    private static Predicate<TypeName> discoveryPredicate(Set<TypeName> extensionAnnotations,
                                                          Collection<String> extensionPackages) {
        List<String> prefixes = extensionPackages.stream()
                .map(it -> it.endsWith(".*") ? it.substring(0, it.length() - 2) : it)
                .toList();

        return typeName -> {
            if (extensionAnnotations.contains(typeName)) {
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

    private static String toPackagePrefix(String configured) {
        if (configured.endsWith(".*")) {
            return configured.substring(0, configured.length() - 1);
        }
        if (configured.endsWith(".")) {
            return configured;
        }
        return configured + ".";
    }

    private List<TypeInfoAndAnnotations> annotatedTypes(List<TypeInfo> allTypes) {
        List<TypeInfoAndAnnotations> result = new ArrayList<>();

        for (TypeInfo typeInfo : allTypes) {
            result.add(new TypeInfoAndAnnotations(typeInfo, TypeHierarchy.nestedAnnotations(ctx, typeInfo)));
        }
        return result;
    }

    private void writeNewTypes(List<ClassCode> toWrite) {
        // after each round, write all generated types
        CodegenFiler filer = ctx.filer();

        // generate all code
        for (var classCode : toWrite) {
            ClassModel classModel = classCode.classModel().build();
            filer.writeSourceFile(classModel, classCode.originatingElements());
        }
    }

    private RoundContextImpl createRoundContext(List<TypeInfoAndAnnotations> annotatedTypes,
                                                ExtensionInfo extension,
                                                List<ClassCode> newTypes) {
        Set<TypeName> availableAnnotations = new HashSet<>();
        Map<TypeName, List<TypeInfo>> annotationToTypes = new HashMap<>();
        Map<TypeName, TypeInfo> processedTypes = new HashMap<>();
        Map<TypeName, Set<TypeName>> metaAnnotationToAnnotations = new HashMap<>();

        // now go through all available annotated types and make sure we only include the ones required by this extension
        for (TypeInfoAndAnnotations annotatedType : annotatedTypes) {
            for (TypeName annotationType : annotatedType.annotations()) {
                boolean metaAnnotated = metaAnnotations(extension, metaAnnotationToAnnotations, annotationType);
                if (metaAnnotated || extension.supportedAnnotationsPredicate().test(annotationType)) {
                    availableAnnotations.add(annotationType);
                    processedTypes.put(annotatedType.typeInfo().typeName(), annotatedType.typeInfo());
                    annotationToTypes.computeIfAbsent(annotationType, k -> new ArrayList<>())
                            .add(annotatedType.typeInfo());
                    // annotation is meta-annotated with a supported meta-annotation,
                    // or we support the annotation type, or it is prefixed by the package prefix
                }
            }
        }

        return new RoundContextImpl(
                ctx,
                newTypes,
                Set.copyOf(availableAnnotations),
                Map.copyOf(annotationToTypes),
                Map.copyOf(metaAnnotationToAnnotations),
                List.copyOf(processedTypes.values()));
    }

    private boolean metaAnnotations(ExtensionInfo extension,
                                    Map<TypeName, Set<TypeName>> metaAnnotationToAnnotations,
                                    TypeName annotationType) {
        Optional<TypeInfo> annotationInfo = ctx.typeInfo(annotationType);
        if (annotationInfo.isEmpty()) {
            return false;
        }
        TypeInfo annotationTypeInfo = annotationInfo.get();

        boolean metaAnnotated = false;
        for (TypeName metaAnnotation : extension.supportedMetaAnnotations()) {
            for (Annotation anAnnotation : annotationTypeInfo.allAnnotations()) {
                if (anAnnotation.typeName().equals(metaAnnotation)) {
                    metaAnnotated = true;
                    metaAnnotationToAnnotations.computeIfAbsent(metaAnnotation, k -> new HashSet<>())
                            .add(annotationType);
                }
            }
        }
        return metaAnnotated;
    }

    private record TypeInfoAndAnnotations(TypeInfo typeInfo, Set<TypeName> annotations) {
    }

    private record ExtensionInfo(CodegenExtension extension,
                                 Predicate<TypeName> supportedAnnotationsPredicate,
                                 Set<TypeName> supportedMetaAnnotations) {
    }
}
