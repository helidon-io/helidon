/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.time.Duration;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.Metric;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddConfig(key = "metrics.enabled", value = "false")
@AddBean(GaugedBean.class)
class TestDisabledMetrics {

    @Test
    void ensureRegistryFactoryIsMinimal() {
        // Invoking instance() should retrieve the factory previously initialized as disabled.
        RegistryFactory rf = RegistryFactory.getInstance();
        /*
         Probe each metric to make sure its delegate is a no-op.
         */
        for (Metric m : rf.getRegistry(Registry.APPLICATION_SCOPE).getMetrics().values()) {
            if (m instanceof HelidonCounter c) {
                c.inc();
                assertThat("Expected counter", c.getCount(), is(0L));
            } else if (m instanceof HelidonHistogram h) {
                h.update(23L);
                assertThat("Histogram count", h.getCount(), is(0L));
            } else if (m instanceof HelidonTimer t) {
                t.update(Duration.ofMillis(123L));
                assertThat("Timer count", t.getCount(), is(0L));
            }
        }
    }
}
