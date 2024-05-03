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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;

import io.helidon.microprofile.testing.mocking.MockBean;
import io.helidon.microprofile.testing.testng.AddBean;
import io.helidon.microprofile.testing.testng.HelidonTest;

import org.mockito.Answers;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.testng.annotations.Test;

@HelidonTest
@AddBean(TestMockBeanField.Resource.class)
@AddBean(TestMockBeanField.Service.class)
public class TestMockBeanField {

    @Inject
    @MockBean
    private Service service;
    @Inject
    private WebTarget target;

    @Test
    void injectionTest() {
        String response = target.path("/test").request().get(String.class);
        // Defaults to specified in @Produces
        assertThat(response, is("Not Mocked"));
        Mockito.when(service.test()).thenReturn("Mocked");
        response = target.path("/test").request().get(String.class);
        assertThat(response, is("Mocked"));
    }

    @Produces
    MockSettings mockSettings() {
        return Mockito.withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS);
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

    static class Service {

        String test() {
            return "Not Mocked";
        }

    }
}
