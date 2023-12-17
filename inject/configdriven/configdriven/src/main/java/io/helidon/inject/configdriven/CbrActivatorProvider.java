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

package io.helidon.inject.configdriven;

import io.helidon.inject.Activator;
import io.helidon.inject.Services;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.spi.ActivatorProvider;

/**
 * {@link java.util.ServiceLoader} service implementation of a custom activator provider used only to initialize
 * Config bean registry.
 */
public class CbrActivatorProvider implements ActivatorProvider {
    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public CbrActivatorProvider() {
    }

    @Override
    public String id() {
        return CbrServiceDescriptor.CBR_RUNTIME_ID;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Activator<T> activator(Services services, ServiceDescriptor<T> descriptor) {
        if (!(descriptor instanceof CbrServiceDescriptor cbrDescriptor)) {
            throw new IllegalArgumentException("Config Driven Activator only supports descriptors of its own type,"
                                                       + " invalid descriptor provided: "
                                                       + descriptor.infoType().fqName());
        }
        return (Activator<T>) new CbrProvider(services, cbrDescriptor);
    }
}
