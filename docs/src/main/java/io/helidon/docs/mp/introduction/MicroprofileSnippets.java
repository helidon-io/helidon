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
package io.helidon.docs.mp.introduction;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;

@SuppressWarnings("ALL")
class MicroprofileSnippets {

    // tag::snippet_1[]
    @Path("/")
    @RequestScoped
    public class HelloWorldResource {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String message() {
            return "Hello World";
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @ApplicationScoped
    @ApplicationPath("/")
    public class HelloWorldApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(
                    HelloWorldResource.class
            );
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    public static void main(String[] args) {
        io.helidon.microprofile.cdi.Main.main(args);
    }
    // end::snippet_3[]

}
