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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.ServiceBinder;

/**
 * An explicit and manually implemented module component, as we are doing a bit of unusual work with config bean registry.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 100)
public class ConfigDrivenInjectModule implements ModuleComponent {
    /**
     * Constructor for ServiceLoader.
     *
     * @deprecated for use by Java ServiceLoader, do not use directly
     */
    @Deprecated
    public ConfigDrivenInjectModule() {
        super();
    }

    @Override
    public String name() {
        return "io.helidon.inject.configdriven";
    }

    @Override
    public void configure(ServiceBinder binder) {
        binder.bind(CbrServiceDescriptor.INSTANCE);
    }
}
