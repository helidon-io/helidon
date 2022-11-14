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

import io.helidon.common.Weight;

import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.spi.ext.Dependencies;
import jakarta.annotation.Generated;
import jakarta.inject.Singleton;

@Generated(value = "TODO: Generate these for real", comments = "API Version: 1")
@Singleton
@Weight(DefaultServiceInfo.DEFAULT_WEIGHT)
@SuppressWarnings("unchecked")
public class WorldImpl$$picodiActivator extends AbstractServiceProvider<WorldImpl> {
    private static final DefaultServiceInfo serviceInfo =
            DefaultServiceInfo.builder()
                    .serviceTypeName(getServiceTypeName())
                    .externalContractImplemented(World.class.getName())
                    .activatorTypeName(WorldImpl$$picodiActivator.class.getName())
                    .scopeTypeName(Singleton.class.getName())
                    .weight(DefaultServiceInfo.DEFAULT_WEIGHT)
                    .build();

    public static final WorldImpl$$picodiActivator INSTANCE = new WorldImpl$$picodiActivator();

    WorldImpl$$picodiActivator() {
        setServiceInfo(serviceInfo);
    }

    public static String getServiceTypeName() {
        return WorldImpl.class.getName();
    }

    public Dependencies dependencies() {
        Dependencies dependencies = Dependencies.builder().forServiceTypeName(getServiceTypeName()).build()
                .build();
        return Dependencies.combine(super.dependencies(), dependencies);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected WorldImpl createServiceProvider(Map<String, Object> deps) {
        return new WorldImpl();
    }

}
