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
package io.helidon.docs.mp;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@SuppressWarnings("ALL")
class MockingSnippets {

    // tag::snippet_1[]
    @ApplicationScoped
    public class FooService { // <1>

        public String getFoo() {
            return "foo";
        }
    }

    @Path("/foo")
    public class FooResource {

        @Inject
        private FooService fooService; // <2>

        @GET
        public String getFoo() {
            return fooService.getFoo();
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @HelidonTest
    @Priority(1) // <1>
    class FooTest {

        @Inject
        private WebTarget target;

        private FooService fooService;

        @BeforeEach
        void initMock() {
            fooService = Mockito.mock(FooService.class, Answers.CALLS_REAL_METHODS); // <2>
        }

        @Produces
        @Alternative
        FooService mockFooService() {
            return fooService; // <3>
        }

        @Test
        void testMockedFoo() {
            when(fooService.getFoo()).thenReturn("bar"); // <4>

            Response response = target.path("/foo").request().get();

            assertThat(response.getStatus(), is(200));
            assertThat(response.readEntity(String.class), is("bar"));
        }

        @Test
        void testFoo() {
            Response response = target.path("/foo").request().get(); // <5>

            assertThat(response.getStatus(), is(200));
            assertThat(response.readEntity(String.class), is("foo"));
        }
    }
    // end::snippet_2[]
}
