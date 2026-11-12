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
package io.helidon.integrations.oci.metrics.otherpkg;

import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@SuppressWarnings("removal")
class OciMetricsLegacyApiTest {
    @Test
    void legacyFormatterStaticMethodRemainsCallableOutsideItsPackage() {
        assertThat(OciMetricsSupport.NameFormatter.textType(mock(Counter.class)), is("counter"));
        assertThat(OciMetricsSupport.NameFormatter.textType(mock(FunctionalCounter.class)), is("counter"));
        assertThat(OciMetricsSupport.NameFormatter.textType(mock(Gauge.class)), is("gauge"));
        assertThat(OciMetricsSupport.NameFormatter.textType(mock(DistributionSummary.class)), is("histogram"));
        assertThat(OciMetricsSupport.NameFormatter.textType(mock(Timer.class)), is("timer"));
        assertThrows(IllegalArgumentException.class, () -> OciMetricsSupport.NameFormatter.textType(mock(Meter.class)));
    }
}
