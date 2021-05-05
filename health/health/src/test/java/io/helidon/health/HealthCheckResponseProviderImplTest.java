/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.health;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HealthCheckResponseProviderImplTest {
    @Test
    void simpleResponseHasNoDataAndIsUp() {
        HealthCheckResponse response = HealthCheckResponse.named("successful-test").build();
        assertThat("successful-test", is(response.getName()));
        assertThat(response.getData(), notNullValue());
        assertThat(response.getData().isPresent(), is(false));
        assertThat(HealthCheckResponse.State.UP, is(response.getState()));
    }

    @Test
    void stateTrueResponseIsUp() {
        HealthCheckResponse response = HealthCheckResponse.named("successful-test").state(true).build();
        assertThat("successful-test", is(response.getName()));
        assertThat(response.getData(), notNullValue());
        assertThat(response.getData().isPresent(), is(false));
        assertThat(HealthCheckResponse.State.UP, is(response.getState()));
    }

    @Test
    void downResponseIsDown() {
        HealthCheckResponse response = HealthCheckResponse.named("failed-test").down().build();
        assertThat("failed-test", is(response.getName()));
        assertThat(response.getData(), notNullValue());
        assertThat(response.getData().isPresent(), is(false));
        assertThat(HealthCheckResponse.State.DOWN, is(response.getState()));
    }

    @Test
    void stateFalseResponseIsDown() {
        HealthCheckResponse response = HealthCheckResponse.named("failed-test").state(false).build();
        assertThat("failed-test", is(response.getName()));
        assertThat(response.getData(), notNullValue());
        assertThat(response.getData().isPresent(), is(false));
        assertThat(HealthCheckResponse.State.DOWN, is(response.getState()));
    }

    @Test
    void dataIsPresentAndSortedLexicographically() {
        HealthCheckResponse response = HealthCheckResponse.named("data-test")
                .withData("foo", "bar")
                .withData("baz", Long.MAX_VALUE)
                .withData("wombat", true)
                .up()
                .build();

        assertThat("data-test", is(response.getName()));
        assertThat(response.getData(), notNullValue());
        assertThat(response.getData().isPresent(), is(true));
        assertThat(HealthCheckResponse.State.UP, is(response.getState()));
        assertThat(3, is(response.getData().get().size()));
        assertThat(response.getData().get(), hasEntry("baz", Long.MAX_VALUE));
        assertThat(response.getData().get(), hasEntry("foo", "bar"));
        assertThat(response.getData().get(), hasEntry("wombat", true));
    }

    @Test
    void nullResponseNameIsNotAccepted() {
        assertThrows(NullPointerException.class, () -> HealthCheckResponse.named(null).build());
    }

    @Test
    void emptyResponseNameIsAccepted() {
        final HealthCheckResponse response = HealthCheckResponse.named("").build();
        assertThat(response.getName().isEmpty(), is(true));
    }

    @Test
    void nullKeyIsNotAccepted() {
        //noinspection CodeBlock2Expr
        assertThrows(NullPointerException.class, () -> {
            HealthCheckResponse.named("null-key-test")
                    .withData(null, "foo")
                    .up()
                    .build();
        });
    }

    @Test
    void builderCanBeReused() {
        // Create the original builder.
        final HealthCheckResponseBuilder builder = HealthCheckResponse.named("reuse-test")
                .withData("foo", "bar")
                .up();

        // We know from previous tests that the builder has the right state.
        final HealthCheckResponse response = builder.build();

        // Now modify the builder
        builder.name("reuse-test2")
                .down()
                .withData("wombat", true);

        // Now get the new response
        final HealthCheckResponse response2 = builder.build();

        // The original "response" must not contain the new values
        assertThat(response.getData(), notNullValue());
        assertThat(response.getData().isPresent(), is(true));
        assertThat(1, is(response.getData().get().size()));
        assertThat(response.getData().get(), hasEntry("foo", "bar"));
        assertThat(HealthCheckResponse.State.UP, is(response.getState()));

        // The new "response2" must contain the old and new data, and the new values
        assertThat("reuse-test2", is(response2.getName()));
        assertThat(HealthCheckResponse.State.DOWN, is(response2.getState()));
        assertThat(response2.getData(), notNullValue());
        assertThat(response2.getData().isPresent(), is(true));
        assertThat(2, is(response2.getData().get().size()));
        assertThat(response2.getData().get(), hasEntry("foo", "bar"));
        assertThat(response2.getData().get(), hasEntry("wombat", true));
    }
}
