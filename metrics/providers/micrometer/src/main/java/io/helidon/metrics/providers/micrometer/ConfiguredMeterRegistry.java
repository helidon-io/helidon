/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.micrometer;

import java.util.function.Supplier;

import io.helidon.common.config.NamedService;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Behavior required of each configured meter registry.
 */
public interface ConfiguredMeterRegistry extends NamedService {

    /**
     * The {@link io.micrometer.core.instrument.MeterRegistry} created by this configured meter registry (if it is enabled).
     *
     * @return a supplier for the Micrometer meter registry that would be created by this group of settings
     */
    Supplier<MeterRegistry> meterRegistry();

    /**
     * Whether the configured meter registry is enabled and should be used.
     *
     * @return true if the meter registry should be used, false otherwise
     */
    // Avoid the method name "enabled" because "enabled" has a default implementation at PushRegistryConfig which some of our
    // config prototypes implement, and the Java compilation detects a conflict.
    boolean isEnabled();

}
