/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddBeans;
import io.helidon.microprofile.testing.junit5.Configuration;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that query params can be sent and resolved as headers for security.
 */
@SuppressWarnings("SpellCheckingInspection")
@AddBeans({
        @AddBean(BindingTest.MyResource.class),
        @AddBean(TestResource1.class),
        @AddBean(TestResource2.class),
})
@Configuration(configSources = "bind-query-params.yaml")
@HelidonTest
public class ExtractQueryParamsTest {

    private static final String USERNAME = "assdlakdfknkasdfvsadfasf";

    @Inject
    private WebTarget webTarget;

    @Test
    public void testBasicHeader() {
        Response response = webTarget.path("/test2")
                .request()
                .header("x-user", USERNAME)
                .get();

        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), containsString(USERNAME));
    }

    @Test
    public void testBasicQuery() {
        Response response = webTarget.path("/test2")
                .queryParam("basicAuth", USERNAME)
                .request()
                .get();

        assertThat("Should have been successful. Headers: " + response.getHeaders(), response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), containsString(USERNAME));
    }

    @Test
    public void testBasicFails() {
        Response response = webTarget.path("/test2")
                .queryParam("wrong", USERNAME)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
    }
}
