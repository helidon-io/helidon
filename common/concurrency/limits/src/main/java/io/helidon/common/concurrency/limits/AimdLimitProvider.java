/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import io.helidon.common.Weight;
import io.helidon.common.concurrency.limits.spi.LimitProvider;
import io.helidon.common.config.Config;

/**
 * {@link java.util.ServiceLoader} service provider for {@link io.helidon.common.concurrency.limits.AimdLimit}
 * limit implementation.
 */
@Weight(80)
public class AimdLimitProvider implements LimitProvider {
    /**
     * Constructor required by the service loader.
     */
    public AimdLimitProvider() {
    }

    @Override
    public String configKey() {
        return AimdLimit.TYPE;
    }

    @Override
    public Limit create(Config config, String name) {
        return AimdLimit.builder()
                .config(config)
                .name(name)
                .build();
    }
}
