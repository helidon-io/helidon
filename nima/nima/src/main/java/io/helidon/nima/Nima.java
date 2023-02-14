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

package io.helidon.nima;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;

/**
 * Application main entry point.
 */
public class Nima {
    private static final AtomicReference<Config> NIMA_CONFIG = new AtomicReference<>();
    private static final LazyValue<Config> CONFIG_INSTANCE = LazyValue.create(() -> {
        Config c = NIMA_CONFIG.get();
        return (c == null) ? Config.create() : c;
    });

    static {
        LogConfig.initClass();
    }

    private Nima() {
    }

    /**
     * Global configuration instance.
     *
     * @return shared configuration loaded either from a profile or the default configuration
     */
    public static Config config() {
        return CONFIG_INSTANCE.get();
    }

    /**
     * Set global configuration. Once method {@link #config()} is invoked, the configuration cannot be modified.
     *
     * @param config configuration to use
     */
    public static void config(Config config) {
        NIMA_CONFIG.set(config);
    }
}
