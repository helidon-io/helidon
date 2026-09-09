/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.annotation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.http.Status;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.security.SecurityCdiExtension;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddExtension;
import io.helidon.microprofile.testing.AddJaxRs;
import io.helidon.microprofile.testing.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

@HelidonTest
@DisableDiscovery
@AddJaxRs
@AddExtension(ConfigCdiExtension.class)
@AddExtension(SecurityCdiExtension.class)
@AddBean(ApplicationSecurityAnnotationTest.TestApplication.class)
@AddBean(ApplicationSecurityAnnotationTest.PublicResource.class)
class ApplicationSecurityAnnotationTest {

    @Test
    void applicationRolesRequireAuthenticationWithoutConstrainingAuthorization(WebTarget target) {
        ApplicationSecurityAnnotationAnalyzer.reset();
        ExpandedApplicationSecurityAnnotationAnalyzer.reset();
        try {
            try (Response response = target.path("/application-security/public")
                    .request()
                    .get()) {
                assertThat(response.getStatus(), is(Status.UNAUTHORIZED_401.code()));
            }
            String basicAuth = "basic " + Base64.getEncoder()
                    .encodeToString("fail:password".getBytes(StandardCharsets.UTF_8));
            try (Response response = target.path("/application-security/public")
                    .request()
                    .header("Authorization", basicAuth)
                    .get()) {
                assertThat(response.getStatus(), is(Status.OK_200.code()));
                assertThat(response.readEntity(String.class), is("public"));
            }
            assertThat(ApplicationSecurityAnnotationAnalyzer.annotationTypes(),
                       hasItem(TypeName.create(RolesAllowed.class)));
            assertThat(ExpandedApplicationSecurityAnnotationAnalyzer.annotationTypes(),
                       hasItem(TypeName.create(RolesAllowed.class)));
        } finally {
            ApplicationSecurityAnnotationAnalyzer.reset();
            ExpandedApplicationSecurityAnnotationAnalyzer.reset();
        }
    }

    /**
     * Application carrying security that is analyzer input but is not inherited as resource authorization.
     */
    @ApplicationScoped
    @ApplicationPath("/application-security")
    @RolesAllowed("admin")
    public static class TestApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(PublicResource.class);
        }
    }

    /**
     * Unprotected resource registered with the secured application.
     */
    @RequestScoped
    @Path("public")
    public static class PublicResource {

        /**
         * Returns the public response.
         *
         * @return public response
         */
        @GET
        public String get() {
            return "public";
        }
    }
}
