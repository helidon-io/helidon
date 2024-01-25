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

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.AddJaxRs;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SuppressWarnings("ALL")
class TestingSnippets {

    // stub
    static class MyBean {
        String greeting() {
            return "";
        }
    }

    // tag::snippet_1[]
    @HelidonTest // <1>
    @DisableDiscovery // <2>
    @AddBean(MyBean.class) // <3>
    @AddExtension(ConfigCdiExtension.class) // <4>
    @AddConfig(key = "app.greeting", value = "TestHello") // <5>
    class TestExample {
        @Inject
        private MyBean myBean; // <6>

        @Test
        void testGreeting() { // <7>
            assertThat(myBean, notNullValue());
            assertThat(myBean.greeting(), is("TestHello"));
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @HelidonTest // <1>
    @DisableDiscovery // <2>
    @AddJaxRs // <3>
    @AddBean(TestReqScopeDisabledDiscovery.MyController.class)
    public class TestReqScopeDisabledDiscovery {

        @Inject
        private WebTarget target;

        @Test
        void testGet() {
            String greeting = target.path("/greeting")
                    .request().get(String.class);
            assertThat(greeting, is("Hallo!"));
        }

        @Path("/greeting")
        @RequestScoped // <4>
        public static class MyController {
            @GET
            public Response get() {
                return Response.ok("Hallo!").build();
            }
        }
    }
    // end::snippet_2[]

}
