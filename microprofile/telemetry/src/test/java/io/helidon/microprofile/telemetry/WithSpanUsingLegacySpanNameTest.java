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
package io.helidon.microprofile.telemetry;

import java.util.stream.Stream;

import io.helidon.microprofile.testing.junit5.AddConfig;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@AddConfig(key = HelidonTelemetryContainerFilter.SPAN_NAME_INCLUDES_METHOD,
           value = "false")
class WithSpanUsingLegacySpanNameTest extends WithSpanTestBase {

    @ParameterizedTest()
    @MethodSource()
    void testDefaultAppSpanNameFromPath(SpanPathTestInfo spanPathTestInfo) {
        testSpanNameFromPath(spanPathTestInfo);
    }

    static Stream<SpanPathTestInfo> testDefaultAppSpanNameFromPath() {
        return Stream.of(new SpanPathTestInfo("traced", "/traced"),
                         new SpanPathTestInfo("traced/sub/data", "/traced/sub/{name}"));
    }
}
