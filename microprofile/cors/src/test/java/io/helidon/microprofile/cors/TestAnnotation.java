/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.cors;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestAnnotation {

    private SeContainer seContainer;

    @Test
    void checkBadAnnotationHandling() {

        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        initializer.addBeanClasses(CorsResourceWithBadAnnotation.class);

        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                    seContainer = initializer.initialize());
            assertThat("Exception error message",
                       e.getMessage(),
                       allOf(containsString("annotation is valid only on @OPTIONS methods"),
                             containsString(CorsResourceWithBadAnnotation.class.getName())));

        } finally {
            if (seContainer != null) {
                seContainer.close();
            }
        }
    }

    @RequestScoped
    @Path("/cors1")
    static class CorsResourceWithBadAnnotation {

        // The following @CrossOrigin should trigger an error during start-up annotation processing
        // because it is not on an @OPTIONS method.
        @CrossOrigin(value = {"http://foo.bar", "http://bar.foo"},
                     allowMethods = {"PUT"})
        @PUT
        @Path("/subpath")
        public Response put() {
            return Response.ok().build();
        }

        @GET
        public Response get() {
            return Response.ok().build();
        }

        @OPTIONS
        @CrossOrigin(value = {"http://foo.bar", "http://bar.foo"},
                     allowMethods = {"PUT"})
        @Path("/subpath")
        public void optionsForSubpath() {
        }

        @OPTIONS
        @CrossOrigin()
        public void optionsForMainPath() {
        }
    }
}
