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

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

@SuppressWarnings("ALL")
class PersistenceSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @Inject // <1>
        @Named("test") // <2>
        private DataSource ds; // <3>
        // end::snippet_1[]
    }

    class Snippet2 {

        class SomeObject {
            // tag::snippet_2[]
            private final DataSource ds; // <1>

            @Inject // <2>
            public SomeObject(@Named("test") DataSource ds) { // <3>
                this.ds = ds; // <4>
            }
            // end::snippet_2[]
        }
    }

    // stub
    static class GreetingProvider {
        void setMessage(String message) {
        }
    }

    class Snippet3 {
        private GreetingProvider greetingProvider;

        // tag::snippet_3[]
        @Transactional // <1>
        public void setGreeting(Integer id) {
            // Do something transactional.
            greetingProvider.setMessage("Hello[" + id + "]"); // <2>
        }
        // end::snippet_3[]
    }

}
