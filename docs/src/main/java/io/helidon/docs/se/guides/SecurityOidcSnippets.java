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
package io.helidon.docs.se.guides;

import java.util.Base64;

import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.security.providers.oidc.OidcFeature;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;

import jakarta.json.JsonObject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class SecurityOidcSnippets {

    void routing(HttpRouting.Builder routing) {
        // tag::snippet_1[]
        Config config = Config.global();
        routing.addFeature(OidcFeature.create(config));   // <1>
        // end::snippet_1[]
    }

    void snippet_2(WebClient client) {
        // tag::snippet_2[]
        try (HttpClientResponse response = client.get()
                .path("/greet")
                .request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }
        // end::snippet_2[]
    }

    void snippet_3(WebClient client) {
        // tag::snippet_3[]
        String auth = "Basic " + Base64.getEncoder().encodeToString("jack:changeit".getBytes());
        JsonObject jsonObject = client.get()
                .path("/greet")
                .header(HeaderNames.AUTHORIZATION, auth)
                .requestEntity(JsonObject.class);

        assertThat(jsonObject.getString("message"), is("Hello World!"));
        // end::snippet_3[]
    }

    void snippet_4(WebClient client) {
        // tag::snippet_4[]
        String auth = "Basic " + Base64.getEncoder().encodeToString("john:changeit".getBytes());
        try (HttpClientResponse response = client.get()
                .path("/greet")
                .header(HeaderNames.AUTHORIZATION, auth)
                .request()) {
            assertThat(response.status(), is(Status.FORBIDDEN_403));
        }
        // end::snippet_4[]
    }
}
