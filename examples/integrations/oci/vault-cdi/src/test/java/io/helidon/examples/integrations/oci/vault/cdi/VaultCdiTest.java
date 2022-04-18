/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.vault.cdi;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This test is not validating OCI integration code itself, as OCI authentication
 * and configuration must be present for it to work.
 * This test just starts the server and makes sure it is available.
 */
@HelidonTest
class VaultCdiTest {
    private final WebTarget target;

    @Inject
    VaultCdiTest(WebTarget target) {
        this.target = target;
    }

    @Test
    void testServerUp() {
        try (Response response = target.path("/health")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(200));
        }
    }
}
