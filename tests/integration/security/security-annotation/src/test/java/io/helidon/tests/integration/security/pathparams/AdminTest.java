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

package io.helidon.tests.integration.security.pathparams;

import java.util.Base64;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class AdminTest {

    @Test
    void testProtectedAdminEndpoint(WebTarget target) {
        testProtectedAdmin(target, "/admin");
    }

    @Test
    void testProtectedAdmin2Endpoint(WebTarget target) {
        testProtectedAdmin(target, "/admin2");
    }

    @Test
    void testUnauthorizedAdminEndpoint(WebTarget target) {
        testUnprotectedAdmin(target, "/admin/unauthorized");
    }

    @Test
    void testUnauthorizedAdmin2Endpoint(WebTarget target) {
        testUnprotectedAdmin(target, "/admin2/unauthorized");
    }

    private void testProtectedAdmin(WebTarget target, String endpoint) {
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("fail"))
                .get()) {
            assertThat(response.getStatus(), is(Status.FORBIDDEN_403.code()));
        }
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("success"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("admin"));
        }
    }

    private void testUnprotectedAdmin(WebTarget target, String endpoint) {
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("fail"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("unauthorized admin"));
        }
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("success"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("unauthorized admin"));
        }
    }

    private String basic(String user) {
        String uap = user + ":password";
        return "basic " + Base64.getEncoder().encodeToString(uap.getBytes());
    }
}
