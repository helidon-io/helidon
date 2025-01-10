/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@HelidonTest
@AddConfig(key = "otel.sdk.disabled", value = "false")
class TestTracerAtStartup {

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "https://github.com/helidon-io/helidon/issues/9513")
    void checkForFullFeaturedTracerAtStartup() {
        assertThat("Global tracer from start-up extension",
                   TestExtension.globalTracerAtStartup.unwrap(io.opentelemetry.api.trace.Tracer.class).getClass().getName(),
                   not(containsString("Default")));
    }
}
