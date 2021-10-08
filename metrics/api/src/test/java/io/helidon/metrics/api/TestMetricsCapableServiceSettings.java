/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestMetricsCapableServiceSettings {

    @Test
    void testWithComponentMetricsDisabled() {

        test(false, 0L);
    }

    @Test
    void testWithComponentMetricsEnabled() {
        test(true, 0L);
    }

    private static void test(boolean componentMetricsEnabled, long expectedCounterValue) {
        ComponentMetricsSettings.Builder cms = ComponentMetricsSettings
                .builder()
                .enabled(componentMetricsEnabled);

        MyMetricsServiceSupport myServiceSupport = MyMetricsServiceSupport.builder().componentMetricsSettings(cms).build();
        myServiceSupport.access();

        assertThat("Counter value with component metrics  = " + componentMetricsEnabled,
                   myServiceSupport.getCount(), is(expectedCounterValue));
    }
}
