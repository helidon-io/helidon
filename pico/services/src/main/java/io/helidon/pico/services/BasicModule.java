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

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Phase;

/**
 * Basic {@link io.helidon.pico.Module} implementation. A Pico module is-a service provider also.
 */
class BasicModule extends AbstractServiceProvider<io.helidon.pico.Module> {

    /**
     * Ctor.
     *
     * @param module the module instance
     * @param moduleName the optional module name
     */
    BasicModule(
            io.helidon.pico.Module module,
            String moduleName) {
        super(module, Phase.ACTIVE, DefaultServiceInfo.builder()
                .moduleName(moduleName)
                .named(moduleName)
                .serviceTypeName(module.getClass().getName())
                .contractImplemented(Module.class.getName())
                .build(),
              null);
        serviceRef(module);
    }

}
