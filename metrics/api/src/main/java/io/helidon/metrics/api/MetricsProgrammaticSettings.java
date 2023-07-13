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
package io.helidon.metrics.api;

import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

/**
 * Programmatic (rather than configured) settings that govern certain metrics behavior.
 * <p>
 *     Implementations of this interface are typically provided by Helidon itself rather than
 *     developers building applications and are not intended for per-deployment (or even per-application)
 *     customization.
 * </p>
 */
public interface MetricsProgrammaticSettings {

    /**
     * Returns the singleton instance of the metrics programmatic settings.
     * @return the singleton
     */
    static MetricsProgrammaticSettings instance() {
        return Instance.INSTANCE.get();
    }

    /**
     * Returns the name to use for a tag, added to each meter's identity, conveying its scope in output.
     *
     * @return the scope tag name
     */
    String scopeTagName();

    /**
     * Returns the name to use for a tag, added to each meter's identity, conveying the application it belongs to.
     *
     * @return the app tag name
     */
    String appTagName();

    /**
     * Returns the reserved tag names (for scope and app).
     *
     * @return reserved tag names
     */
    default Set<String> reservedTagNames() {
        return Set.of(scopeTagName(), appTagName());
    }

    /**
     * Internal use class to hold a reference to the singleton.
     */
    class Instance {

        private Instance() {
        }

        private static final LazyValue<MetricsProgrammaticSettings> INSTANCE = LazyValue.create(() ->
                        HelidonServiceLoader.builder(ServiceLoader.load(MetricsProgrammaticSettings.class))
                                .addService(new NoOpMetricsProgrammaticSettings(), Double.MIN_VALUE)
                                .build()
                                .asList()
                                .get(0));
    }
}
