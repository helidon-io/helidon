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
package io.helidon.docs.mp.jaxrs;

import io.helidon.webserver.http.ServerRequest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;

@SuppressWarnings("ALL")
class JaxrsApplicationsSnippets {

    // tag::snippet_1[]
    @Path("myresource")
    public class MyResource {

        @GET
        public void get(@Context ServerRequest serverRequest) {
            Application app = serverRequest.context().get(Application.class).get();
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @ApplicationPath("/my-application")
    public class MyApplication extends Application {

    }
    // end::snippet_2[]
}
