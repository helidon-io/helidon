/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.testng.AddBean;
import io.helidon.microprofile.testing.testng.AddConfig;
import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(TestNamedWebTarget.ResourceClass.class)
@AddConfig(key = "server.sockets.0.port", value = "0")
@AddConfig(key = "server.sockets.0.name", value = "named")
public class TestNamedWebTarget {

    @Inject
    private WebTarget target;

    @Inject
    @Socket("named")
    private WebTarget namedTarget;

    @Test
    void testTargetsAreDifferent(){
        //Should be different
        assertThat(target.getUri(), not(namedTarget.getUri()));
    }

    @Test
    void testRegularTarget() {
        assertThat(target, notNullValue());
        String response = target.path("/test")
                .request()
                .get(String.class);
        assertThat(response, is("Hello from ResourceClass"));
    }

    @Test
    void testNamedSocketTarget() {
        assertThat(namedTarget, notNullValue());
        String response = namedTarget.path("/test/named")
                .request()
                .get(String.class);
        assertThat(response, is("Hello from Named Resource"));
    }

    @Path("/test")
    public static class ResourceClass {
        @GET
        public String getIt() {
            return "Hello from ResourceClass";
        }

        @GET
        @Path("named")
        public String getNamed() {
            return "Hello from Named Resource";
        }
    }
}
