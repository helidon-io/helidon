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

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@AddBean(App.class)
@AddBean(AppTracedResource.class)
class WithSpanWithExplicitAppUsingLegacyNamingTest extends WithSpanTestBase {

        @ParameterizedTest()
        @MethodSource()
        void testExplicitAppSpanNameFromPath(SpanPathTestInfo spanPathTestInfo) {
            testSpanNameFromPath(spanPathTestInfo);
        }

        static Stream<SpanPathTestInfo> testExplicitAppSpanNameFromPath() {
            return Stream.of(new SpanPathTestInfo("topapp/apptraced", "/topapp/apptraced"),
                             new SpanPathTestInfo("topapp/apptraced/sub/data", "/topapp/apptraced/sub/{name}"));
        }
}
