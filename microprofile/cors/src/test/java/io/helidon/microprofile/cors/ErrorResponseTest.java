/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_METHOD;
import static org.hamcrest.CoreMatchers.hasItem;
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

    @Inject
    private WebTarget target;

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Test
    void testErrorResponse() {
        Response res = target.path("/notfound")
                .request()
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .get();
        assertThat("Status from missing endpoint request", res.getStatusInfo(), is(Response.Status.NOT_FOUND));
        assertThat("With CORS enabled, headers in 404 response", res.getHeaders().keySet(), hasItem(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
