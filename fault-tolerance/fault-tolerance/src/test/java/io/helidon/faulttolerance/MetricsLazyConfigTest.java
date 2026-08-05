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

package io.helidon.faulttolerance;

import io.helidon.config.Config;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class MetricsLazyConfigTest {
    @Test
    void testGlobalMetricsDoNotActivateConfig() {
        ServiceRegistry registry = GlobalServiceRegistry.registry();
        assertThat(registry.firstActive(Config.class).isEmpty(), is(true));

        Retry retry = Retry.builder()
                .name("inactive-config")
                .build();

        retry.invoke(() -> 0);

        assertThat(registry.firstActive(Config.class).isEmpty(), is(true));
    }
}
