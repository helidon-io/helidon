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

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.Option;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.codegen.spi.CodegenProvider;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation for {@link io.helidon.codegen.spi.CodegenExtensionProvider}
 * that handles Helidon Service Registry code generation.
 */
public class ServiceRegistryCodegenProvider implements CodegenExtensionProvider {
    private static final List<RegistryCodegenExtensionProvider> EXTENSIONS =
            HelidonServiceLoader.create(ServiceLoader.load(RegistryCodegenExtensionProvider.class,
                                                           ServiceRegistryCodegenProvider.class.getClassLoader()))
                    .asList();

    private static final Set<Option<?>> SUPPORTED_OPTIONS =
            EXTENSIONS.stream()
                    .map(CodegenProvider::supportedOptions)
                    .flatMap(Set::stream)
                    .collect(Collectors.toUnmodifiableSet());

    private static final Set<TypeName> SUPPORTED_ANNOTATIONS =
            Stream.concat(EXTENSIONS.stream()
                                  .map(RegistryCodegenExtensionProvider::supportedAnnotations)
                                  .flatMap(Set::stream),
                          Stream.of(TypeNames.GENERATED,
                                    ServiceCodegenTypes.SERVICE_ANNOTATION_DESCRIPTOR))
                    .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> SUPPORTED_ANNOTATION_PACKAGES =
            EXTENSIONS.stream()
                    .map(RegistryCodegenExtensionProvider::supportedAnnotationPackages)
                    .flatMap(Set::stream)
                    .collect(Collectors.toUnmodifiableSet());

    private static final Set<TypeName> SUPPORTED_META_ANNOTATIONS =
            EXTENSIONS.stream()
                    .map(RegistryCodegenExtensionProvider::supportedMetaAnnotations)
                    .flatMap(Set::stream)
                    .collect(Collectors.toUnmodifiableSet());

    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public ServiceRegistryCodegenProvider() {
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return SUPPORTED_OPTIONS;
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return SUPPORTED_ANNOTATIONS;
    }

    @Override
    public Set<String> supportedAnnotationPackages() {
        return SUPPORTED_ANNOTATION_PACKAGES;
    }

    @Override
    public Set<TypeName> supportedMetaAnnotations() {
        return SUPPORTED_META_ANNOTATIONS;
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return ServiceRegistryCodegenExtension.create(ctx, generatorType, EXTENSIONS);
    }
}
