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

import io.helidon.common.Weighted;
import io.helidon.inject.Activator;
import io.helidon.inject.Services;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.spi.ActivatorProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation of an {@link io.helidon.inject.spi.ActivatorProvider} that
 * supports config driven beans.
 */
public class ConfigDrivenActivatorProvider implements ActivatorProvider, Weighted {
    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public ConfigDrivenActivatorProvider() {
    }

    @Override
    public String id() {
        return "CONFIG_DRIVEN";
    }

    @Override
    public <T> Activator<T> activator(Services services, ServiceDescriptor<T> descriptor) {
        return ConfigDrivenServiceProvider.create(services, descriptor);
    }

    @Override
    public double weight() {
        // less than default, so others can override it
        return Weighted.DEFAULT_WEIGHT - 10;
    }
}
