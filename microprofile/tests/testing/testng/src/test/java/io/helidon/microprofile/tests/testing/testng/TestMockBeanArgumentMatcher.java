/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testing.testng;

import io.helidon.microprofile.testing.mocking.MockBean;
import io.helidon.microprofile.testing.testng.AddBean;
import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.WebTarget;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

@HelidonTest
@AddBean(TestMockBeanArgumentMatcher.Resource.class)
@AddBean(TestMockBeanArgumentMatcher.Service.class)
public class TestMockBeanArgumentMatcher {

    @MockBean
    private Service service;

    @Inject
    private WebTarget target;

    @Test
    void testArgumentMatcher() {
        Mockito.when(service.test(anyString())).thenReturn("Mocked");
        String response = target.path("/test")
                .queryParam("str", "anything")
                .request()
                .get(String.class);
        assertThat(response, is("Mocked"));
    }

    @Path("/test")
    public static class Resource {

        @Inject
        private Service service;

        @GET
        public String post(@QueryParam("str") String str) {
            return service.test(str);
        }
    }

    @ApplicationScoped
    static class Service {

        String test(String str) {
            return "Not Mocked: " + str;
        }
    }
}
