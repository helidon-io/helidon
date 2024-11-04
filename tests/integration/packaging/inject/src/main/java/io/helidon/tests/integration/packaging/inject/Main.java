/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.packaging.inject;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Lookup;

/**
 * We must provide a main class when using modularized jar file with main class attribute,
 * as we cannot use a main class from another module (at least not easily).
 */
public class Main {
    static {
        LogConfig.initClass();
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        // makes sure global config is initialized
        Config config = GlobalConfig.config();
        GlobalConfig.config(() -> config);

        InjectRegistry registry = InjectRegistryManager.create()
                .registry();
        registry.get(Lookup.builder()
                             .runLevel(Injection.RunLevel.SERVER)
                             .build());
    }
}
