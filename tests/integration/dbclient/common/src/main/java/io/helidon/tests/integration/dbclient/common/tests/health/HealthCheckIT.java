/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.health;

import io.helidon.dbclient.health.DbClientHealthCheck;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verify that health check works.
 */
public class HealthCheckIT {

    /**
     * Verify health BASIC check implementation.
     */
    @Test
    public void testHealthCheck() {
        HealthCheck check = DbClientHealthCheck.create(DB_CLIENT);
        HealthCheckResponse response = check.call();
        HealthCheckResponse.State state = response.getState();
        assertThat("Healthcheck failed, response: " + response.getData(), state, equalTo(HealthCheckResponse.State.UP));
    }

    /**
     * Verify health check implementation with builder and custom name.
     */
    @Test
    public void testHealthCheckWithName() {
        final String hcName = "TestHC";
        HealthCheck check = DbClientHealthCheck.builder(DB_CLIENT).name(hcName).build();
        HealthCheckResponse response = check.call();
        String name = response.getName();
        HealthCheckResponse.State state = response.getState();
        assertThat(name, equalTo(hcName));
        assertThat(state, equalTo(HealthCheckResponse.State.UP));
    }

}
