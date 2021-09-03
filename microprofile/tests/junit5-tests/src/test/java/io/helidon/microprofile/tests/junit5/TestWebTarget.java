/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.junit5;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;

import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddBean(TestWebTarget.ResourceClass.class)
class TestWebTarget {
    @Test
    void testFirst(WebTarget target) {
        assertThat(target, notNullValue());
        String response = target.path("/test")
                .request()
                .get(String.class);
        assertThat(response, is("Hello from ResourceClass"));
    }

    @Test
    void testSecond(WebTarget target) {
        assertThat(target, notNullValue());
        String response = target.path("/test")
                .request()
                .get(String.class);
        assertThat(response, is("Hello from ResourceClass"));
    }

    @Path("/test")
    public static class ResourceClass {
        @GET
        public String getIt() {
            return "Hello from ResourceClass";
        }
    }
}