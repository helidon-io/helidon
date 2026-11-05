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

package io.helidon.webserver.benchmark.jmh;

import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HealthAggregateJmhSmokeTest {
    @ParameterizedTest
    @CsvSource({"1, 0", "4, 0", "8, 0", "1, 1", "4, 1", "8, 1"})
    void aggregateHealthReturnsEveryCheck(int checkCount, int delayMillis) {
        var benchmark = new HealthAggregateJmhBenchmark();
        benchmark.checkCount = checkCount;
        benchmark.delayMillis = delayMillis;
        benchmark.setup();
        try {
            // Repeated requests exercise the reusable client and full response consumption.
            for (int request = 0; request < 2; request++) {
                JsonObject result = JsonParser.create(benchmark.aggregateHealth()).readJsonObject();
                assertThat(result.stringValue("status").orElseThrow(), is("UP"));
                var checks = result.arrayValue("checks").orElseThrow();
                assertThat(checks.size(), is(checkCount));
                for (int i = 0; i < checkCount; i++) {
                    JsonObject check = (JsonObject) checks.values().get(i);
                    assertThat(check.stringValue("name").orElseThrow(), is("check-" + i));
                    assertThat(check.stringValue("status").orElseThrow(), is("UP"));
                    assertThat(check.objectValue("data").orElseThrow().stringValue("value").orElseThrow(), is("value-" + i));
                }
            }
        } finally {
            benchmark.tearDown();
        }
    }
}
