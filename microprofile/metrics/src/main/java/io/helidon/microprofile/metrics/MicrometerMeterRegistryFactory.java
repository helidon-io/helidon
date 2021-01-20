/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import io.helidon.config.Config;
import io.helidon.metrics.MicrometerSupport;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicReference;

public final class MicrometerMeterRegistryFactory {

    private static final MicrometerMeterRegistryFactory INSTANCE = create();
    private final AtomicReference<Config> config;
    private MeterRegistry meterRegistry;

    private MicrometerMeterRegistryFactory(Config config) {
        this.config = new AtomicReference<>(config);
        meterRegistry = MicrometerSupport.create(config).registry();
    }

    public static MicrometerMeterRegistryFactory create() {
        return create(Config.empty());
    }

    public static MicrometerMeterRegistryFactory create(Config config) {
        return new MicrometerMeterRegistryFactory(config);
    }

    public static MicrometerMeterRegistryFactory getInstance() {
        return INSTANCE;
    }

    public static MicrometerMeterRegistryFactory getInstance(Config config) {
        INSTANCE.update(config);
        return INSTANCE;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    private void update(Config config) {
        this.config.set(config);
    }
}
