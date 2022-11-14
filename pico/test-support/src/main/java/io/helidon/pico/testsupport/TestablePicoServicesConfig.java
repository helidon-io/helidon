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

package io.helidon.pico.testsupport;

import java.util.function.Supplier;

import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.spi.impl.DefaultPicoServicesConfig;

/**
 * A version of {@link io.helidon.pico.spi.impl.DefaultPicoServicesConfig} that is conducive to unit testing.
 */
public class TestablePicoServicesConfig implements PicoServicesConfig {

    private final DefaultPicoServicesConfig cfg = new DefaultPicoServicesConfig();

    /**
     * Sets up developer test-friendly configuration.
     */
    public TestablePicoServicesConfig() {
        setValue(PicoServicesConfig.KEY_ACTIVATION_LOGS_ENABLED, true);
        setValue(PicoServicesConfig.KEY_SUPPORTS_DYNAMIC, true);
    }

    @Override
    public <T> T value(String key, Supplier<T> defaultValueSupplier) {
        return cfg.value(key, defaultValueSupplier);
    }

    /**
     * Sets the value of key to val.
     *
     * @param key the key
     * @param val the value
     * @return the old value
     * @param <T> the type
     */
    public <T> T setValue(String key, T val) {
        return cfg.setValue(key, val);
    }

}
