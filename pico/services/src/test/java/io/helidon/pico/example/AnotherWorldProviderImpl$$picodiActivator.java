/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.example;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Weight;

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.spi.ext.Dependencies;
import jakarta.annotation.Generated;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Generated(value = "TODO: Generate these for real", comments = "API Version: 1")
@Singleton
@Weight(DefaultServiceInfo.DEFAULT_WEIGHT)
@SuppressWarnings("unchecked")
public class AnotherWorldProviderImpl$$picodiActivator extends AbstractServiceProvider<AnotherWorldProviderImpl> {
    public AnotherWorldProviderImpl$$picodiActivator() {
        setServiceInfo(DefaultServiceInfo.builder()
                .serviceTypeName(getServiceTypeName())
                .contractImplemented(World.class.getName())
                .activatorTypeName(AnotherWorldProviderImpl$$picodiActivator.class.getName())
                .scopeTypeName(Singleton.class.getName())
                .weight(DefaultServiceInfo.DEFAULT_WEIGHT)
                .build());
    }

    public static String getServiceTypeName() {
        return WorldImpl.class.getName();
    }

    public Dependencies dependencies() {
        Dependencies deps = Dependencies.builder().forServiceTypeName(getServiceTypeName())
                .add(InjectionPointInfo.CTOR, World.class, InjectionPointInfo.ElementKind.CTOR, 1, InjectionPointInfo.Access.PUBLIC)
                .elemOffset(1).setIsOptionalWrapped().setIsProviderWrapped()
                .build().build();
        return Dependencies.combine(super.dependencies(), deps);
    }

    @Override
    public boolean isProvider() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AnotherWorldProviderImpl createServiceProvider(Map<String, Object> deps) {
        Optional<Provider<World>> arg1 = (Optional<Provider<World>>) Objects.requireNonNull(deps.get("io.helidon.pico.example.<init>|1(1)"), deps.toString());
        return new AnotherWorldProviderImpl(arg1);
    }

}
