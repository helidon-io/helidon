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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@SuppressWarnings("ALL")
class BeanvalidationSnippets {

    static class Greeting {
    }

    // tag::snippet_1[]
    @Path("helloworld")
    public class HelloWorld {

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        public void post(@NotNull @Valid Greeting greeting) {
            // ...
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    public class GreetingHolder {
        @NotNull
        private String greeting;
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @ApplicationScoped
    public class GreetingProvider {
        private GreetingHolder greetingHolder;

        void setGreeting(@Valid GreetingHolder greetingHolder) {
            this.greetingHolder = greetingHolder;
        }
    }
    // end::snippet_3[]

}
