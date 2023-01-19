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

import io.helidon.pico.Application;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Phase;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceInfo;

/**
 * Basic {@link io.helidon.pico.Application} implementation. A Pico application is-a service provider also.
 */
class BasicApplicationServiceProvider extends AbstractServiceProvider<Application> {

    BasicApplicationServiceProvider(
            Application app,
            PicoServices picoServices) {
        super(app, Phase.ACTIVE, createServiceInfo(app), picoServices);
        serviceRef(app);
    }

    @SuppressWarnings("rawtypes")
    static ServiceInfo createServiceInfo(
            Application app) {
        DefaultServiceInfo.Builder builder = DefaultServiceInfo.builder()
                .serviceTypeName(app.getClass().getName())
                .addContractsImplemented(Application.class.getName());
        app.name().ifPresent(name -> builder.addQualifier(DefaultQualifierAndValue.createNamed(name)));
        return builder.build();
    }

}
