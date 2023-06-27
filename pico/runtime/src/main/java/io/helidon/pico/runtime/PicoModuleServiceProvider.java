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

package io.helidon.pico.runtime;

import io.helidon.common.types.TypeName;
import io.helidon.pico.api.ModuleComponent;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Qualifier;
import io.helidon.pico.api.ServiceInfo;

/**
 * Basic {@link ModuleComponent} implementation. A Pico module is-a service provider also.
 */
class PicoModuleServiceProvider extends AbstractServiceProvider<ModuleComponent> {

    PicoModuleServiceProvider(ModuleComponent module,
                              String moduleName,
                              PicoServices picoServices) {
        super(module, PicoServices.terminalActivationPhase(), createServiceInfo(module, moduleName), picoServices);
        serviceRef(module);
    }

    static ServiceInfo createServiceInfo(ModuleComponent module,
                                         String moduleName) {
        ServiceInfo.Builder builder = ServiceInfo.builder()
                .serviceTypeName(TypeName.create(module.getClass()))
                .addContractImplemented(TypeName.create(ModuleComponent.class));
        if (moduleName != null) {
            builder.moduleName(moduleName)
                    .addQualifier(Qualifier.createNamed(moduleName));
        }
        return builder.build();
    }

    @Override
    public Class<?> serviceType() {
        return ModuleComponent.class;
    }
}
