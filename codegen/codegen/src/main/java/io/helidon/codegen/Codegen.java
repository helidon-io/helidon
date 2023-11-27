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

package io.helidon.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
import io.helidon.common.types.TypedElementInfo;

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

    private final Map<TypeName, List<CodegenExtension>> typeToExtensions = new HashMap<>();
    private final Map<CodegenExtension, Predicate<TypeName>> extensionPredicates = new IdentityHashMap<>();
    private final CodegenContext ctx;
    private final List<CodegenExtension> extensions;
    private final Set<TypeName> supportedAnnotations;
    private final Set<String> supportedPackagePrefixes;

    private Codegen(CodegenContext ctx, TypeName generator) {
        this.ctx = ctx;

        this.extensions = EXTENSIONS.stream()
                .map(it -> {
                    CodegenExtension extension = it.create(this.ctx, generator);

                    for (TypeName typeName : it.supportedAnnotations()) {
                        typeToExtensions.computeIfAbsent(typeName, key -> new ArrayList<>())
                                .add(extension);
                    }
                    Collection<String> packages = it.supportedAnnotationPackages();
                    if (!packages.isEmpty()) {
                        extensionPredicates.put(extension, discoveryPredicate(packages));
                    }

                    return extension;
                })
                .toList();

        // handle supported annotations and package prefixes
        Set<String> packagePrefixes = new HashSet<>();
        Set<TypeName> annotations = new HashSet<>(ctx.mapperSupportedAnnotations());

        for (CodegenExtensionProvider extension : EXTENSIONS) {
            annotations.addAll(extension.supportedAnnotations());

            ctx.mapperSupportedAnnotationPackages()
                    .stream()
                    .map(Codegen::toPackagePrefix)
                    .forEach(packagePrefixes::add);
        }
        ctx.mapperSupportedAnnotationPackages()
                .stream()
                .map(Codegen::toPackagePrefix)
                .forEach(packagePrefixes::add);

        this.supportedAnnotations = Set.copyOf(annotations);
        this.supportedPackagePrefixes = Set.copyOf(packagePrefixes);
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
        return new Codegen(ctx, generator);
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

        for (CodegenExtension extension : extensions) {
            // and now for each extension, we discover types that contain annotations supported by that extension
            // and create a new round context for each extension

            RoundContextImpl roundCtx = createRoundContext(annotatedTypes, extension);
            extension.process(roundCtx);
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
        for (CodegenExtension extension : extensions) {
            RoundContextImpl roundCtx = createRoundContext(List.of(), extension);
            extension.processingOver(roundCtx);
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

    private static Predicate<TypeName> discoveryPredicate(Collection<String> packages) {
        List<String> prefixes = packages.stream()
                .map(it -> it.endsWith(".*") ? it.substring(0, it.length() - 2) : it)
                .toList();
        return typeName -> {
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
            result.add(new TypeInfoAndAnnotations(typeInfo, annotations(typeInfo)));
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

    private RoundContextImpl createRoundContext(List<TypeInfoAndAnnotations> annotatedTypes, CodegenExtension extension) {
        Set<TypeName> extAnnots = new HashSet<>();
        Map<TypeName, List<TypeInfo>> extAnnotToType = new HashMap<>();
        Map<TypeName, TypeInfo> extTypes = new HashMap<>();

        for (TypeInfoAndAnnotations annotatedType : annotatedTypes) {
            for (TypeName typeName : annotatedType.annotations()) {
                boolean added = false;
                List<CodegenExtension> validExts = this.typeToExtensions.get(typeName);
                if (validExts != null) {
                    for (CodegenExtension validExt : validExts) {
                        if (validExt == extension) {
                            extAnnots.add(typeName);
                            extAnnotToType.computeIfAbsent(typeName, key -> new ArrayList<>())
                                    .add(annotatedType.typeInfo());
                            extTypes.put(annotatedType.typeInfo().typeName(), annotatedType.typeInfo);
                            added = true;
                        }
                    }
                }
                if (!added) {
                    Predicate<TypeName> predicate = this.extensionPredicates.get(extension);
                    if (predicate != null && predicate.test(typeName)) {
                        extAnnots.add(typeName);
                        extAnnotToType.computeIfAbsent(typeName, key -> new ArrayList<>())
                                .add(annotatedType.typeInfo());
                        extTypes.put(annotatedType.typeInfo().typeName(), annotatedType.typeInfo);
                    }
                }
            }
        }

        return new RoundContextImpl(
                Set.copyOf(extAnnots),
                Map.copyOf(extAnnotToType),
                List.copyOf(extTypes.values()));
    }

    private Set<TypeName> annotations(TypeInfo theTypeInfo) {
        Set<TypeName> result = new HashSet<>();

        // on type
        theTypeInfo.annotations()
                .stream()
                .map(Annotation::typeName)
                .forEach(result::add);

        // on fields, methods etc.
        theTypeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::annotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        // on parameters
        theTypeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::parameterArguments)
                .flatMap(List::stream)
                .map(TypedElementInfo::annotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        return result;
    }

    private record TypeInfoAndAnnotations(TypeInfo typeInfo, Set<TypeName> annotations) {
    }
}
