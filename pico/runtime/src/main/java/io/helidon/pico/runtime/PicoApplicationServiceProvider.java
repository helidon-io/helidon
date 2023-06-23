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
import io.helidon.pico.api.Application;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Qualifier;
import io.helidon.pico.api.ServiceInfo;

/**
 * Basic {@link io.helidon.pico.api.Application} implementation. A Pico application is-a service provider also.
 */
class PicoApplicationServiceProvider extends AbstractServiceProvider<Application> {

    PicoApplicationServiceProvider(Application app, PicoServices picoServices) {
        super(app, Phase.ACTIVE, createServiceInfo(app), picoServices);
        serviceRef(app);
    }

    static ServiceInfo createServiceInfo(Application app) {
        ServiceInfo.Builder builder = ServiceInfo.builder()
                .serviceTypeName(app.getClass())
                .addContractImplemented(TypeName.create(Application.class));
        app.named().ifPresent(name -> builder.addQualifier(Qualifier.createNamed(name)));
        return builder.build();
    }

    @Override
    public Class<?> serviceType() {
        return Application.class;
    }
}
