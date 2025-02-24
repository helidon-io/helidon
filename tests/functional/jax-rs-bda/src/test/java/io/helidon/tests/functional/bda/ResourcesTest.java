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
package io.helidon.tests.functional.bda;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class ResourcesTest {

    @Inject
    private WebTarget target;

    /**
     * Test that resource {@code "app1/greet1"} exists as part of
     * {@link io.helidon.tests.functional.bda.HelloWorld1}.
     */
    @Test
    void testHelloWorld1() {
        Response response = target.path("app1")
                .path("greet1")
                .request()
                .get();
        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is("hello1"));
    }

    /**
     * Test that resource {@code "app2/greet2"} exists as part of
     * {@link io.helidon.tests.functional.bda.HelloWorld2}.
     */
    @Test
    void testHelloWorld2() {
        Response response = target.path("app2")
                .path("greet2")
                .request()
                .get();
        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is("hello2"));
    }

    /**
     * Test that resource {@code "app3/greet3"} does not exist since no
     * synthetic app shall be created in this case.
     */
    @Test
    void testHelloWorld3() {
        Response response = target.path("/")
                .path("greet3")
                .request()
                .get();
        assertThat(response.getStatus(), is(404));
    }
}
