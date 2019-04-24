/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.server;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class ConstantHealthCheckTest {
    @Test
    public void shouldBeUp() {
        HealthCheck check = ConstantHealthCheck.up("foo");
        HealthCheckResponse response = check.call();

        assertThat(response.getName(), is("foo"));
        assertThat(response.getState(), is(HealthCheckResponse.State.UP));
        assertThat(response.getData(), is(notNullValue()));
    }

    @Test
    public void shouldBeDown() {
        HealthCheck check = ConstantHealthCheck.down("foo");
        HealthCheckResponse response = check.call();

        assertThat(response.getName(), is("foo"));
        assertThat(response.getState(), is(HealthCheckResponse.State.DOWN));
        assertThat(response.getData(), is(notNullValue()));
    }
}
