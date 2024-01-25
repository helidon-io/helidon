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
package io.helidon.docs.mp.integrations;

import com.oracle.bmc.objectstorage.ObjectStorage;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@SuppressWarnings("ALL")
class OciSnippets {

    // tag::snippet_1[]
    @Inject
    private ObjectStorage client;
    // end::snippet_1[]

    // tag::snippet_2[]
    public class MyClass {

        private final ObjectStorage client;

        @Inject
        public MyClass(@Named("orders") ObjectStorage client) {
            this.client = client;
        }
    }
    // end::snippet_2[]

}
