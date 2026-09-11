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

package io.helidon.declarative.tests.compatibility.app;

import java.util.List;
import java.util.Optional;

import io.helidon.declarative.tests.compatibility.v4.LegacyAdditionalHttpEndpoint;
import io.helidon.declarative.tests.compatibility.v4.LegacyAdditionalRestClient;
import io.helidon.http.Status;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webclient.api.RestClient;
import io.helidon.webclient.http1.Http1Client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@SuppressWarnings({"helidon:api:incubating", "helidon:api:preview"})
final class RestCompatibilityChecks {
    private RestCompatibilityChecks() {
    }

    static void verify(ServiceRegistry registry, Http1Client client) {
        LegacyAdditionalRestClient typedClient = registry.get(Lookup.builder()
                                                                     .addContract(LegacyAdditionalRestClient.class)
                                                                     .addQualifier(Qualifier.create(RestClient.Client.class))
                                                                     .build());
        LegacyAdditionalHttpEndpoint endpoint = registry.get(LegacyAdditionalHttpEndpoint.class);

        assertThat("Present optional query reaches the legacy route", typedClient.query(Optional.of(37)), is("present:37"));
        assertThat("Absent optional query stays absent", typedClient.query(Optional.empty()), is("absent"));
        assertThat("Named function computes the request header", typedClient.computedHeader(), is("legacy-computed"));

        int initialEmptyCalls = endpoint.emptyCalls();
        var emptyResponse = client.get("/legacy/additional/empty").request(Void.class);
        assertThat("Legacy void route preserves its explicit status", emptyResponse.status(), is(Status.NO_CONTENT_204));
        assertThat(endpoint.emptyCalls(), is(initialEmptyCalls + 1));
        typedClient.empty();
        assertThat("Legacy void client invokes the route", endpoint.emptyCalls(), is(initialEmptyCalls + 2));

        typedClient.submit("legacy-void-payload");
        assertThat("Legacy void client submits its entity", endpoint.submittedEntity(), is("legacy-void-payload"));
        assertThat("Legacy generic response round-trips through JSON", typedClient.values(), is(List.of("legacy", "generic")));
    }
}
