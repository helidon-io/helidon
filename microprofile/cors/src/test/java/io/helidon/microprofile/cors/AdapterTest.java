/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.AddBean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@AddBean(AdapterTest.TestApp.class)
@AddBean(AdapterTest.TestResource.class)
public class AdapterTest extends BaseCrossOriginTest {

    private static final String APP_PATH = "/adaptertestapp";
    private static final String RESOURCE_PATH = "/adaptertestresource";
    private static final String SUBRESOURCE_PATH = "/subresource";

    private static final String TEST_ID_HEADER = "X-TestId";

    @ApplicationScoped
    @ApplicationPath(APP_PATH)
    static class TestApp extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(TestResource.class);
        }
    }

    @RequestScoped
    @Path(RESOURCE_PATH)
    public static class TestResource {
        @GET
        @Path(SUBRESOURCE_PATH)
        public Response subpathGet() {
            return Response.ok().build();
        }

        @GET
        public Response resourceGet() {
            return Response.ok().build();
        }
    }

    @Inject
    private WebTarget webTarget;

    @Test
    void testForFullPath() {
        testPath(APP_PATH + RESOURCE_PATH + SUBRESOURCE_PATH, "fullPath");
    }

    @Test
    void testForResourcePath() {
        testPath(APP_PATH + RESOURCE_PATH, "resourcePathOnly");
    }

    private void testPath(String requestPath, String testId) {
        Response response = webTarget.path(requestPath)
                .request()
                .header(TEST_ID_HEADER, testId)
                .get();
        assertThat("Response status", response.getStatus(), is(Status.OK_200.code()));
        assertThat("Adapter path", TestFilter.adapters.get(testId).path(), is(requestPath));
    }

    public static class TestFilter implements ContainerRequestFilter {

        private static final Map<String, CorsSupportMp.RequestAdapterMp> adapters = new HashMap<>();

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            String testId = requestContext.getHeaderString(TEST_ID_HEADER);
            if (testId != null && !testId.isBlank()) {
                adapters.put(testId, new CorsSupportMp.RequestAdapterMp(requestContext));
            }
        }
    }
}
