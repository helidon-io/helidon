/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.cors;

import io.helidon.http.HeaderNames;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderNames.ORIGIN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@HelidonTest
@AddBean(CrossOriginTest.CorsResource0.class)
@AddBean(CrossOriginTest.CorsResource1.class)
@AddBean(CrossOriginTest.CorsResource2.class)
@AddBean(CrossOriginTest.CorsResource3.class)
@AddConfig(key = "cors.paths.0.path-pattern", value = "/cors3")
@AddConfig(key = "cors.paths.0.allow-origins", value = "http://foo.bar, http://bar.foo")
@AddConfig(key = "cors.paths.0.allow-methods", value = "DELETE, PUT")
@AddConfig(key = "cors.enabled", value = "false")
class CorsDisabledTest {

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Inject
    private WebTarget target;

    @Test
    void testCorsIsDisabled() {
        Response res = target.path("/cors2")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .put(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        assertThat(res.getStatusInfo(), is(Response.Status.OK));
        assertThat("Headers from successful response",
                   res.getHeaders().keySet(),
                   not(hasItem(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase())));
    }
}
