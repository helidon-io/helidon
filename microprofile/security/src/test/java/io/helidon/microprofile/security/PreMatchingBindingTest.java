/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddBeans;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;

import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that Jersey binding works.
 */
@AddBeans({
        @AddBean(PreMatchingBindingTest.MyResource.class),
})
@Configuration(configSources = "pre-matching.yaml")
@AddExtension(SecurityCdiExtension.class)
@HelidonTest
public class PreMatchingBindingTest {

    @Inject
    private WebTarget webTarget;

    @Test
    public void testPublic() {
        Response response = webTarget
                .path("/")
                .request()
                .get();

        // this must be forbidden, as we use a pre-matching filter
        assertThat(response.getStatus(), is(401));
    }

    @Test
    public void testAuthenticated() {
        Response response = webTarget
                .path("/")
                .request()
                .header("x-user", "jack")
                .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat(entity, is("hello jack"));
    }

    @Test
    public void testDeny() {
        //this should fail
        try {
            webTarget
                    .path("/deny")
                    .request()
                    // we must authenticate, as we use a pre-matching filter
                    .header("x-user", "jack")
                    .get(String.class);
            fail("The deny path should have been forbidden by authorization provider");
        } catch (ForbiddenException ignored) {
            //this is expected
        }
    }

    @Path("/")
    public static class MyResource {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String getIt(@Context SecurityContext context) {
            return "hello" +
                    context.user()
                            .map(Subject::principal)
                            .map(Principal::getName)
                            .map(name -> " " + name)
                            .orElse("");
        }

        @GET
        @Path("deny")
        public String denyIt() {
            return "shouldNotGet";
        }
    }
}

