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
package io.helidon.docs.mp.testing;

import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class TestingNgSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @HelidonTest
        @Priority(1) // <3>
        class MyTest {

            @Inject
            WebTarget target;

            MyService myService;

            @BeforeMethod
            void initMock() {
                myService = Mockito.mock(MyService.class, Answers.CALLS_REAL_METHODS); // <1>
            }

            @Produces
            @Alternative // <2>
            MyService mockService() {
                return myService;
            }

            @Test
            void testService() {
                Mockito.when(myService.test()).thenReturn("Mocked"); // <4>
                Response response = target.path("/test").request().get();
                assertThat(response, is("Mocked"));
            }
        }

        @Path("/test")
        class MyResource {

            @Inject
            MyService myService;

            @GET
            String test() {
                return myService.test();
            }
        }

        @ApplicationScoped
        class MyService {

            String test() {
                return "Not Mocked";
            }
        }
        // end::snippet_1[]
    }
}
