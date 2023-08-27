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
package io.helidon.microprofile.metrics;

import io.helidon.metrics.api.Metrics;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MetricsTestsRegistryJunitExtension implements Extension,
                                                           BeforeAllCallback {

    static void clear() {
        // Clears out all Registry instances.

        RegistryFactory.erase();

        // Removes meters one at a time, invoking the callbacks so downstream consumers also clear out.
        Metrics.globalRegistry().clear();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        clear();
    }
}
