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
package io.helidon.metrics.providers.micrometer;

import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.MetricsFactory;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MicrometerMetricsTestsJunitExtension implements Extension,
                                                             BeforeAllCallback {

    static void clear() {

        MetricsFactory.closeAll();

        // And clear out Micrometer's global registry explicitly to be extra sure.
        io.micrometer.core.instrument.MeterRegistry mmGlobal = io.micrometer.core.instrument.Metrics.globalRegistry;
        mmGlobal.clear();

        int delayMS = 250;
        int maxSecondsToWait = 5;
        int iterationsRemaining = (maxSecondsToWait * 1000) / delayMS;

        while (iterationsRemaining > 0 && !mmGlobal.getMeters().isEmpty()) {
            iterationsRemaining--;
            try {
                TimeUnit.MILLISECONDS.sleep(delayMS);
            } catch (InterruptedException e) {
                throw new RuntimeException("Error awaiting clear-out of meter registries to finish", e);
            }
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        clear();
    }
}
