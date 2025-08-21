/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

// tag::snippet_0_imports[]
import java.util.Objects;
import io.helidon.discovery.Discovery;
import io.helidon.service.registry.Service;
// end::snippet_0_imports[]

// tag::snippet_1_imports[]
import io.helidon.discovery.Discovery;
import io.helidon.service.registry.Services;
// end::snippet_1_imports[]

// tag::snippet_2_imports[]
import java.net.URI;
import java.util.SequencedSet;
import io.helidon.discovery.DiscoveredUri;
import io.helidon.discovery.Discovery;
// end::snippet_2_imports[]

@SuppressWarnings("ALL")
class DiscoverySnippets {

    private Discovery discovery;

    // tag::snippet_0[]
    public class MyClass {

        private final Discovery discovery;

        @Service.Inject // <1>
        public MyClass(Discovery discovery) { // <2>
            this.discovery = Objects.requireNonNull(discovery, "discovery"); // <3>
        }

    }
    // end::snippet_0[]

    // tag::snippet_1[]
    public class MyOtherClass {

        private final Discovery discovery;

        public MyOtherClass() {
            this.discovery = Services.get(Discovery.class); // <1>
        }

    }
    // end::snippet_1[]

    void snippet_2() {
        // tag::snippet_2[]
        SequencedSet<DiscoveredUri> uris = // <1>
            discovery.uris("EXAMPLE", // <2>
                           URI.create("http://example.com/")); // <3>
        URI uri = uris.getFirst().uri(); // <4>
        // end::snippet_2[]
    }

}
