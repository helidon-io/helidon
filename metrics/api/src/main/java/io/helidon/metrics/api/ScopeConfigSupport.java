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

import io.helidon.builder.api.Prototype;

class ScopeConfigSupport {

    private ScopeConfigSupport() {
    }

    /**
     * Indicates whether the specified meter is enabled according to the scope configuration.
     *
     * @param scopeConfig scope configuration
     * @param name        meter name to check
     * @return whether the meter is enabled
     */
    @Prototype.PrototypeMethod
    static boolean isMeterEnabled(ScopeConfig scopeConfig, String name) {
        // TODO actually do the filtering using the include and exclude patterns
        return scopeConfig.enabled();
    }
}
