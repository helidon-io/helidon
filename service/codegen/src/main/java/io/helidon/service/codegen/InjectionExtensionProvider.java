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

import io.helidon.codegen.Option;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.spi.InjectCodegenObserverProvider;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation that adds code generation for Helidon Inject.
 * This extension creates service descriptors, and intercepted types.
 */
public class InjectionExtensionProvider implements RegistryCodegenExtensionProvider {
    private static final List<InjectCodegenObserverProvider> OBSERVER_PROVIDERS =
            HelidonServiceLoader.create(ServiceLoader.load(InjectCodegenObserverProvider.class,
                                                           InjectionExtension.class.getClassLoader()))
                    .asList();

    /**
     * Required default constructor for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public InjectionExtensionProvider() {
        super();
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return Stream.concat(Stream.of(ServiceOptions.AUTO_ADD_NON_CONTRACT_INTERFACES,
                                       InjectOptions.INTERCEPTION_STRATEGY,
                                       InjectOptions.SCOPE_META_ANNOTATIONS,
                                       InjectOptions.INJECTION_MAIN_CLASS),
                             OBSERVER_PROVIDERS.stream()
                                     .map(InjectCodegenObserverProvider::supportedOptions)
                                     .flatMap(Set::stream))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(ServiceCodegenTypes.INJECTION_SINGLETON,
                      ServiceCodegenTypes.INJECTION_INJECT,
                      ServiceCodegenTypes.INJECTION_INSTANCE,
                      ServiceCodegenTypes.INJECTION_REQUEST_SCOPE,
                      ServiceCodegenTypes.INJECTION_MAIN,
                      ServiceCodegenTypes.INJECTION_DESCRIBE);
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext codegenContext) {
        return new InjectionExtension(codegenContext);
    }
}
