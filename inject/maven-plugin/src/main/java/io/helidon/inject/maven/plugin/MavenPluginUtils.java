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

package io.helidon.inject.maven.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.config.Config;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Phase;
import io.helidon.inject.RegistryServiceProvider;

final class MavenPluginUtils {
    private MavenPluginUtils() {
    }

    /**
     * Returns a {@link io.helidon.inject.Services} registry that forces application loading to be disabled.
     *
     * @return injection services
     */
    static InjectionServices injectionServices(boolean wantApps) {
        return InjectionServices.create(basicConfig(wantApps));
    }

    /**
     * Describe the provided instance or provider.
     *
     * @param providerOrInstance the instance to provider
     * @return the description of the instance
     */
    static String toDescription(Object providerOrInstance) {
        if (providerOrInstance instanceof Optional<?> opt) {
            providerOrInstance = opt.orElse(null);
        }

        if (providerOrInstance instanceof RegistryServiceProvider<?> sp) {
            return sp.description();
        }
        return String.valueOf(providerOrInstance);
    }

    /**
     * Describe the provided instance or provider collection.
     *
     * @param coll the instance to provider collection
     * @return the description of the instance
     */
    static List<String> toDescriptions(Collection<?> coll) {
        return coll.stream().map(MavenPluginUtils::toDescription).collect(Collectors.toList());
    }

    static InjectionConfig basicConfig(boolean apps) {
        return InjectionConfig.builder()
                .useApplication(apps)
                .permitsDynamic(true)
                .limitRuntimePhase(Phase.GATHERING_DEPENDENCIES) // we must not create any instance (may have side effects)
                .serviceConfig(Config.empty()) // we must not use actual application config!
                .build();
    }
}
