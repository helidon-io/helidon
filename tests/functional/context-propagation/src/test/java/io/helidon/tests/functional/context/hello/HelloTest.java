/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.context.hello;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link HelloResource}.
 */
@HelidonTest
class HelloTest {
    private final WebTarget baseTarget;

    @Inject
    HelloTest(WebTarget baseTarget) {
        this.baseTarget = baseTarget;
    }

    @Test
    void testHello() {
        WebTarget target = baseTarget.path("/hello");
        assertOk(target.request().get(), "Hello World");
    }

    @Test
    void testHelloTimeout() {
        WebTarget target = baseTarget.path("/helloTimeout");
        assertOk(target.request().get(), "Hello World");
    }

    @Test
    void testHelloAsync() {
        WebTarget target = baseTarget.path("/helloAsync");
        assertOk(target.request().get(), "Hello World");
    }

    @Test
    void testRemoteAddress() {
        WebTarget target = baseTarget.path("/remoteAddress");
        assertThat(target.request().get().getStatus(), is(200));
    }

    private void assertOk(Response response, String expectedMessage) {
        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is(expectedMessage));
    }
}