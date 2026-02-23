/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderNames.ORIGIN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(CrossOriginTest.CorsResource0.class)
@AddBean(CrossOriginTest.CorsResource1.class)
@AddBean(CrossOriginTest.CorsResource2.class)
@AddBean(CrossOriginTest.CorsResource3.class)
@AddConfig(key = "cors.paths.0.path-pattern", value = "/cors3")
@AddConfig(key = "cors.paths.0.allow-origins", value = "http://foo.bar, http://bar.foo")
@AddConfig(key = "cors.paths.0.allow-methods", value = "DELETE, PUT")
class ErrorResponseTest {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Inject
    private WebTarget target;

    @Test
    void testErrorResponse() {
        Response res = target.path("/notfound")
                .request()
                .header(ORIGIN.defaultCase(), "http://foo.bar")
                .header(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD.defaultCase(), "GET")
                .get();
        assertThat("Status from missing endpoint request", res.getStatusInfo(), is(Response.Status.NOT_FOUND));
        // the 404 is returned from Helidon WebServer, not from Jersey, so the CORS is not present
        // as we may route to additional services after Jersey is resolved
//        assertThat("With CORS enabled, headers in 404 response",
//                   res.getHeaders().keySet(),
//                   hasItem(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.defaultCase()));
    }
}
