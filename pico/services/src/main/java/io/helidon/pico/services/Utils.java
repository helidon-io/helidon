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

package io.helidon.pico.services;

import java.util.Set;

import io.helidon.pico.Application;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;

/**
 * Public helpers around shared Pico services usages.
 */
public class Utils {

    private Utils() {
    }

    /**
     * Determines if the service provider is valid to receive injections.
     *
     * @param sp the service provider
     * @return true if the service provider can receive injection
     */
    public static boolean isQualifiedInjectionTarget(
            ServiceProvider<?> sp) {
        ServiceInfo serviceInfo = sp.serviceInfo();
        Set<String> contractsImplemented = serviceInfo.contractsImplemented();
        DependenciesInfo deps = sp.dependencies();
        return (deps != AbstractServiceProvider.NO_DEPS)
                || (!contractsImplemented.isEmpty()
                    && !contractsImplemented.contains(io.helidon.pico.Module.class.getName())
                    && !contractsImplemented.contains(Application.class.getName()));
    }

}
