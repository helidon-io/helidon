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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.AnnotationMapperProvider;
import io.helidon.codegen.spi.CodegenProvider;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.ElementMapperProvider;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.codegen.spi.TypeMapperProvider;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Base of codegen context implementation taking care of the common parts of the API.
 */
public abstract class CodegenContextBase implements CodegenContext {
    // class -> method name -> element signature
    private final Map<TypeName, Map<String, ElementSignatures>> uniqueNames = new HashMap<>();
    private final List<ElementMapper> elementMappers;
    private final List<TypeMapper> typeMappers;
    private final List<AnnotationMapper> annotationMappers;
    private final Set<Option<?>> supportedOptions;
    private final Set<String> supportedPackages;
    private final Set<TypeName> supportedAnnotations;
    private final CodegenOptions options;
    private final CodegenFiler filer;
    private final CodegenLogger logger;
    private final CodegenScope scope;

    /**
     * Create a new instance with the common parts of the API.
     *
     * @param options           codegen options for the current environment
     * @param additionalOptions additional options to add to the list of supported options
     * @param filer             filer abstraction for the current environment
     * @param logger            logger abstraction for the current environment
     * @param scope             scope of the current environment
     */
    protected CodegenContextBase(CodegenOptions options,
                                 Set<Option<?>> additionalOptions,
                                 CodegenFiler filer,
                                 CodegenLogger logger,
                                 CodegenScope scope) {
        this.options = options;
        this.filer = filer;
        this.logger = logger;
        this.scope = scope;
        Set<Option<?>> supportedOptions = new HashSet<>(additionalOptions);
        Set<String> supportedPackages = new HashSet<>();
        Set<TypeName> supportedAnnotations = new HashSet<>();

        this.annotationMappers = HelidonServiceLoader.create(
                        ServiceLoader.load(AnnotationMapperProvider.class,
                                           CodegenContextBase.class.getClassLoader()))
                .stream()
                .peek(it -> addSupported(it, supportedOptions, supportedPackages, supportedAnnotations))
                .map(it -> it.create(options))
                .toList();

        this.elementMappers = HelidonServiceLoader.create(
                        ServiceLoader.load(ElementMapperProvider.class,
                                           CodegenContextBase.class.getClassLoader()))
                .stream()
                .peek(it -> addSupported(it, supportedOptions, supportedPackages, supportedAnnotations))
                .map(it -> it.create(options))
                .toList();

        this.typeMappers = HelidonServiceLoader.create(
                        ServiceLoader.load(TypeMapperProvider.class,
                                           CodegenContextBase.class.getClassLoader()))
                .stream()
                .peek(it -> addSupported(it, supportedOptions, supportedPackages, supportedAnnotations))
                .map(it -> it.create(options))
                .toList();

        this.supportedOptions = Set.copyOf(supportedOptions);
        this.supportedPackages = Set.copyOf(supportedPackages);
        this.supportedAnnotations = Set.copyOf(supportedAnnotations);

        supportedOptions.forEach(it -> it.findValue(options));
    }

    @Override
    public List<ElementMapper> elementMappers() {
        return elementMappers;
    }

    @Override
    public List<TypeMapper> typeMappers() {
        return typeMappers;
    }

    @Override
    public List<AnnotationMapper> annotationMappers() {
        return annotationMappers;
    }

    @Override
    public Set<TypeName> mapperSupportedAnnotations() {
        return supportedAnnotations;
    }

    @Override
    public Set<String> mapperSupportedAnnotationPackages() {
        return supportedPackages;
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return supportedOptions;
    }

    @Override
    public CodegenFiler filer() {
        return filer;
    }

    @Override
    public CodegenLogger logger() {
        return logger;
    }

    @Override
    public CodegenScope scope() {
        return scope;
    }

    @Override
    public CodegenOptions options() {
        return options;
    }

    @Override
    public String uniqueName(TypeInfo type, TypedElementInfo element) {
        return uniqueNames.computeIfAbsent(type.typeName(), it -> new HashMap<>())
                .computeIfAbsent(element.elementName(), ElementSignatures::new)
                .uniqueName(element.signature());
    }

    private static void addSupported(CodegenProvider provider,
                                     Set<Option<?>> supportedOptions,
                                     Set<String> supportedPackages,
                                     Set<TypeName> supportedAnnotations) {
        supportedOptions.addAll(provider.supportedOptions());
        supportedAnnotations.addAll(provider.supportedAnnotations());
        provider.supportedAnnotationPackages()
                .stream()
                .map(it -> it.endsWith(".*") ? it : it + ".*")
                .forEach(supportedPackages::add);
    }

    private static class ElementSignatures {
        private final Map<ElementSignature, String> names = new HashMap<>();
        private final String name;

        private ElementSignatures(String name) {
            this.name = name;
        }

        public String uniqueName(ElementSignature signature) {
            int size = names.size();
            if (names.containsKey(signature)) {
                return names.get(signature);
            }
            String nextName = size == 0 ? name : name + "_" + size;
            names.put(signature, nextName);
            return nextName;
        }
    }
}
