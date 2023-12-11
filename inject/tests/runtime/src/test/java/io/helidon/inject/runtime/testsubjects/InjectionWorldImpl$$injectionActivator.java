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

package io.helidon.inject.runtime.testsubjects;

import java.util.Map;

import io.helidon.common.Generated;
import io.helidon.common.Weight;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.runtime.AbstractServiceProvider;
import io.helidon.inject.runtime.Dependencies;

import jakarta.inject.Singleton;

import static io.helidon.inject.api.ServiceInfoBasics.DEFAULT_INJECT_WEIGHT;

@Generated(value = "example", comments = "API Version: n", trigger = "io.helidon.inject.runtime.testsubjects.InjectionWorldImpl")
@Singleton
@Weight(DEFAULT_INJECT_WEIGHT)
public class InjectionWorldImpl$$injectionActivator extends AbstractServiceProvider<InjectionWorldImpl> {
    private static final ServiceInfo serviceInfo =
            ServiceInfo.builder()
                    .serviceTypeName(InjectionWorldImpl.class)
                    .activatorTypeName(InjectionWorldImpl$$injectionActivator.class)
                    .addExternalContractImplemented(InjectionWorld.class)
                    .addScopeTypeName(Singleton.class)
                    .declaredWeight(DEFAULT_INJECT_WEIGHT)
                    .build();

    public static final InjectionWorldImpl$$injectionActivator INSTANCE = new InjectionWorldImpl$$injectionActivator();

    InjectionWorldImpl$$injectionActivator() {
        serviceInfo(serviceInfo);
    }

    @Override
    public DependenciesInfo dependencies() {
        DependenciesInfo dependencies = Dependencies.builder(InjectionWorldImpl.class)
                .build();
        return Dependencies.combine(super.dependencies(), dependencies);
    }

    @Override
    protected InjectionWorldImpl createServiceProvider(Map<String, Object> deps) {
        return new InjectionWorldImpl();
    }

    @Override
    public Class<InjectionWorldImpl> serviceType() {
        return InjectionWorldImpl.class;
    }
}
