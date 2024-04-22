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
package io.helidon.tracing.providers.jaeger;

import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

class BuilderPropagatorDefaultTest {

    @Test
    void checkThatPropagatorFormatAssignmentOverridesJaegerDefault() {
        JaegerTracerBuilder builder = JaegerTracerBuilder.create();
        builder.addPropagation(JaegerTracerBuilder.PropagationFormat.B3_SINGLE);
        var props = builder.createPropagators();
        assertThat("Propagators when default overridden", props, not(hasItem(instanceOf(JaegerPropagator.class))));
    }

    @Test
    void checkDefaultedPropagationFormatIsJaeger() {
        JaegerTracerBuilder builder = JaegerTracerBuilder.create();
        var props = builder.createPropagators();
        assertThat("Propagators with only default", props, contains(instanceOf(JaegerPropagator.class)));
    }
}
