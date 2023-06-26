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
package io.helidon.metrics;

import io.helidon.metrics.api.MetricsProgrammaticSettings;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;

import jakarta.json.JsonObject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestJsonFormatter {

    private static JsonFormatter formatter;
    private static Registry appRegistry = RegistryFactory.getInstance().getRegistry(Registry.APPLICATION_SCOPE);
    private static Registry myRegistry = RegistryFactory.getInstance().getRegistry("jsonFormatterTestScope");

    @BeforeAll
    static void init() {
        formatter = JsonFormatter.builder()
                .scopeTagName(MetricsProgrammaticSettings.instance().scopeTagName())
                .build();
    }
    @Test
    void testCounter() {
        Counter counter1 = appRegistry.counter("jsonCounter1");
        counter1.inc(2L);
        Counter counter2 = myRegistry.counter("jsonCounter2", new Tag("t1", "1"));
        counter2.inc(3L);

        JsonObject result = formatter.data(false);

        assertThat("Counter 1",
                   result.getJsonNumber("jsonCounter1;h_scope=application").intValue(),
                   is(2));
        assertThat("Counter 2",
                   result.getJsonNumber("jsonCounter2;h_scope=jsonFormatterTestScope;t1=1").intValue(),
                   is(3));



    }
}
