/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.health;

import io.helidon.common.config.Config;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;

/**
 * {@link java.util.ServiceLoader} provider implementation for health observe provider.
 *
 * @deprecated this type is only to be used from {@link java.util.ServiceLoader}
 */
@Deprecated
public class HealthObserveProvider implements ObserveProvider {
    /**
     * Default constructor required by {@link java.util.ServiceLoader}. Do not use.
     *
     * @deprecated this constructor must be public for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public HealthObserveProvider() {
    }

    @Override
    public String configKey() {
        return "health";
    }

    @Override
    public Observer create(Config config, String name) {
        return HealthObserverConfig.builder()
                .config(config)
                .name(name)
                .build();
    }
}
