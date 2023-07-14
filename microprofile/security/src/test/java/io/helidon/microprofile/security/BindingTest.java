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

import java.lang.reflect.Proxy;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddBeans;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.security.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that Jersey binding works.
 */
@SuppressWarnings({"SpellCheckingInspection", "resource"})
@AddBeans({
        @AddBean(BindingTest.MyResource.class),
        @AddBean(TestResource1.class),
        @AddBean(TestResource2.class),
})
@Configuration(configSources = "bind.yaml")
@AddExtension(SecurityCdiExtension.class)
@HelidonTest
public class BindingTest {

    @Inject
    private WebTarget webTarget;

    @Test
    @Disabled("We must use random ports for tests, not sure how to handle that in web target injection yet.")
    public void testInjection() {
        String username = "SAFAxvdfaLDKJFSlkJSS";
        // call TestResource1 with x-user header
        TestResource1.TransferObject response = webTarget.path("/test1")
                                                         .request()
                                                         .header("x-user", username)
                                                         .get(TestResource1.TransferObject.class);

        // field should be a proxy
        assertThat(response.getFieldClass(), response.isField(), is(true));
        // parameter should NOT be a proxy - we may need to "leak" security context from request scope!!!
        assertThat(response.getParamClass(), response.isParam(), is(false));
        assertThat(response.getSubject(), containsString(username));
    }

    @Test
    public void testBindToJersey() {
        Response response = webTarget.path("/")
                                     .request()
                                     .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat(entity, is("hello"));

        //this should fail
        try {
            ClientBuilder.newClient()
                         .target("http://localhost:" + webTarget.getUri().getPort())
                         .path("/deny")
                         .request()
                         .get(String.class);
            fail("The deny path should have been forbidden by authorization provider");
        } catch (ForbiddenException ignored) {
            //this is expected
        }
    }

    @Test
    public void testContextProvider() {
        Response response = webTarget.path("/scProvider")
                                     .request()
                                     .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat("Injected Provider<SecurityContext> was null", entity, containsString("null=false"));
        assertThat("Injected Provider<SecurityContext> returned null", entity, containsString("contentNull=false"));
        // this is still a proxy, not sure why
        //        assertThat("Injected Provider<SecurityContext> is a proxy", entity, containsString("proxy=false"));
    }

    @Test
    public void testContextInstance() {
        Response response = webTarget.path("/scInstance")
                                     .request()
                                     .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat("Injected SecurityContext was null", entity, containsString("null=false"));
        assertThat("Injected SecurityContext was a proxy", entity, containsString("proxy=false"));
    }

    @Test
    public void testContextParameter() {
        Response response = webTarget.path("/scParam")
                                     .request()
                                     .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat("Injected SecurityContext was null", entity, containsString("null=false"));
        assertThat("Injected SecurityContext was a proxy", entity, containsString("proxy=false"));
    }

    @Path("/")
    public static class MyResource {
        @Inject
        private Provider<SecurityContext> scProvider;

        @Context
        private SecurityContext scInstance;

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        @Path("scParam")
        public String checkScParam(@Context SecurityContext context) {
            if (null == context) {
                return "null=true";
            }
            return "null=false,proxy=" + Proxy.isProxyClass(context.getClass());
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        @Path("scProvider")
        public String checkScProvider() {
            if (null == scProvider) {
                return "null=true";
            }
            SecurityContext context = scProvider.get();
            if (null == context) {
                return "null=false,contentNull=true";
            }

            return "null=false,contentNull=false,proxy=" + Proxy.isProxyClass(context.getClass());
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        @Path("scInstance")
        public String checkScInstance() {
            if (null == scInstance) {
                return "null=true";
            }
            return "null=false,proxy=" + Proxy.isProxyClass(scInstance.getClass());
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String getIt() {
            scProvider.get().isUserInRole("test");
            return "hello";
        }

        @GET
        @Path("deny")
        public String denyIt() {
            return "shouldNotGet";
        }
    }
}

