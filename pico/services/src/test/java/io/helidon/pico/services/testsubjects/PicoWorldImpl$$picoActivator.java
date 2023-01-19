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

package io.helidon.pico.services.testsubjects;

import java.util.Map;

import io.helidon.common.Weight;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.services.AbstractServiceProvider;
import io.helidon.pico.services.Dependencies;

import jakarta.annotation.Generated;
import jakarta.inject.Singleton;

@Generated(value = "example", comments = "API Version: n")
@Singleton
@Weight(DefaultServiceInfo.DEFAULT_WEIGHT)
@SuppressWarnings("unchecked")
public class PicoWorldImpl$$picoActivator extends AbstractServiceProvider<PicoWorldImpl> {
    private static final DefaultServiceInfo serviceInfo =
            DefaultServiceInfo.builder()
                    .serviceTypeName(getServiceTypeName())
                    .activatorTypeName(PicoWorldImpl$$picoActivator.class.getName())
                    .addExternalContractsImplemented(PicoWorld.class.getName())
                    .addScopeTypeName(Singleton.class.getName())
                    .declaredWeight(ServiceInfo.DEFAULT_WEIGHT)
                    .build();

    public static final PicoWorldImpl$$picoActivator INSTANCE = new PicoWorldImpl$$picoActivator();

    PicoWorldImpl$$picoActivator() {
        serviceInfo(serviceInfo);
    }

    public static String getServiceTypeName() {
        return PicoWorldImpl.class.getName();
    }

    @Override
    public DependenciesInfo dependencies() {
        DependenciesInfo dependencies = Dependencies.builder(getServiceTypeName())
                .build();
        return Dependencies.combine(super.dependencies(), dependencies);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected PicoWorldImpl createServiceProvider(Map<String, Object> deps) {
        return new PicoWorldImpl();
    }

}
