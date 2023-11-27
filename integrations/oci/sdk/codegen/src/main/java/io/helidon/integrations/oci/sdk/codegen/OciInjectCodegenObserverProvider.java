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

package io.helidon.integrations.oci.sdk.codegen;

import java.util.Set;
import java.util.function.Function;

import io.helidon.codegen.Option;
import io.helidon.common.GenericType;
import io.helidon.inject.codegen.InjectionCodegenContext;
import io.helidon.inject.codegen.spi.InjectCodegenObserver;
import io.helidon.inject.codegen.spi.InjectCodegenObserverProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation
 * for {@link io.helidon.inject.codegen.spi.InjectCodegenObserverProvider} that creates an observer watching for
 * injections of OCI types, and that generates appropriate services for them.
 */
public class OciInjectCodegenObserverProvider implements InjectCodegenObserverProvider {
    static final Option<Set<String>> OPTION_TYPENAME_EXCEPTIONS =
            Option.createSet("inject.oci.codegenExclusions",
                             "Set of type to exclude from generation",
                             Set.of(),
                             Function.identity(),
                             new GenericType<Set<String>>() { });
    static final Option<Set<String>> OPTION_NO_DOT_EXCEPTIONS =
            Option.createSet("inject.oci.builderNameExceptions",
                             "Set of types that do not use a dot between type and builder.",
                             Set.of(),
                             Function.identity(),
                             new GenericType<Set<String>>() { });

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public OciInjectCodegenObserverProvider() {
    }

    @Override
    public InjectCodegenObserver create(InjectionCodegenContext context) {
        return new OciInjectionCodegenObserver(context);
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return Set.of(OPTION_NO_DOT_EXCEPTIONS,
                      OPTION_TYPENAME_EXCEPTIONS);
    }
}
