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

package io.helidon.inject.runtime;

import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServicesConfig;

/**
 * The default reference implementation {@link InjectionServicesConfig}.
 * <p>
 * It is strongly suggested that any {@link Bootstrap} configuration is established prior to initializing
 * this instance, since the results will vary once any bootstrap configuration is globally set.
 */
class DefaultInjectionServicesConfig {

    static final String PROVIDER = "oracle";

    private DefaultInjectionServicesConfig() {
    }

    static InjectionServicesConfig.Builder createDefaultConfigBuilder() {
        return InjectionServicesConfig.builder()
                .providerName(PROVIDER)
                .providerVersion(Versions.CURRENT_INJECT_VERSION);
    }

}
