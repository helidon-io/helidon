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

package io.helidon.microprofile.tests.testing.junit5;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.testing.junit5.MockBean;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@HelidonTest
@AddBean(TestMockBean.Resource.class)
//@AddBean(TestMockBean.Service.class)
//@AddBean(TestMockBean.OtherService.class)
public class TestMockBean {

    // Without @Inject
    @MockBean
    private Service service;
    // With @Inject
    @Inject
    @MockBean
    private OtherService otherService;
    @Inject
    private WebTarget target;

    @Test
    public void injectionTest() {
        Mockito.when(service.test()).thenReturn("Mocked");
        String response = target.path("/test").request().get(String.class);
        assertThat(response, is("Mocked"));
        Mockito.when(otherService.test()).thenReturn("Mocked");
        assertThat(otherService.test(), is("Mocked"));
    }

    @Path("/test")
    public static class Resource {

        @Inject
        private Service service;

        @GET
        public String test() {
            return service.test();
        }
    }

    public static class Service {

        public String test() {
            return "Not Mocked";
        }

    }

    public static class OtherService {

        public String test() {
            return "OtherService";
        }

    }
}
