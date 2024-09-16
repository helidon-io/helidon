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

package io.helidon.service.tests.inject.configdriven;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.service.inject.api.ConfigDriven;
import io.helidon.service.inject.api.Injection;

@Injection.Singleton
@Weight(90)
// to update a service to be used by service further up, we must have the same qualifiers as the service itself
@ConfigDriven.ConfigBean
class JConfigUpdater implements Supplier<io.helidon.service.tests.inject.configdriven.JConfig.Builder> {
    private final JConfig.Builder config;

    JConfigUpdater(@ConfigDriven.ConfigBean io.helidon.service.tests.inject.configdriven.JConfig.Builder config) {
        this.config = config;
    }

    @Override
    public JConfig.Builder get() {
        return config.value("updated " + config.value());
    }
}
