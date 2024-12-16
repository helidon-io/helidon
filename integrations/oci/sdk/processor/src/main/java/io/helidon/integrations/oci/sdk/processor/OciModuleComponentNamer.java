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

package io.helidon.integrations.oci.sdk.processor;

import java.util.Collection;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.tools.spi.ModuleComponentNamer;

import static java.util.function.Predicate.not;

/**
 * Avoids using any OCI SDK package name(s) as the {@link ModuleComponent} name that is code-generated.
 *
 * @deprecated replaced with {@code helidon-integrations-oci} module
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public class OciModuleComponentNamer implements ModuleComponentNamer {

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public OciModuleComponentNamer() {
    }

    @Override
    public Optional<String> suggestedPackageName(Collection<TypeName> typeNames) {
        String suggested = typeNames.stream()
                .sorted()
                .filter(not(it -> it.name().startsWith(OciInjectionProcessorObserver.GENERATED_OCI_ROOT_PACKAGE_NAME_PREFIX)))
                .filter(not(it -> it.name().startsWith(OciInjectionProcessorObserver.OCI_ROOT_PACKAGE_NAME_PREFIX)))
                .map(TypeName::packageName)
                .findFirst().orElse(null);
        return Optional.ofNullable(suggested);
    }

}
