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

package io.helidon.tests.integration.security.annotation;

import java.util.Base64;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class PathSpecificSecurityTest {

    @Test
    void testPathSpecificSecurityIsNotCachedByResourceMethod(WebTarget target) {
        PathSpecificSecurityResource.resetSecretAccesses();

        assertPublic(target, "public/prime");
        assertThat(PathSpecificSecurityResource.secretAccesses(), is(0));

        try (Response response = target.path("/path-specific/private/secret")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Status.UNAUTHORIZED_401.code()));
        }
        assertThat(PathSpecificSecurityResource.secretAccesses(), is(0));

        try (Response response = target.path("/path-specific/private/secret")
                .request()
                .header("Authorization", basic("success"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("success:private/secret"));
        }
        assertThat(PathSpecificSecurityResource.secretAccesses(), is(1));

        assertPublic(target, "public/again");
        assertThat(PathSpecificSecurityResource.secretAccesses(), is(1));
    }

    private void assertPublic(WebTarget target, String path) {
        try (Response response = target.path("/path-specific/" + path)
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("anonymous:" + path));
        }
    }

    private String basic(String user) {
        String uap = user + ":password";
        return "basic " + Base64.getEncoder().encodeToString(uap.getBytes());
    }
}
