/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.io.IOException;
import java.util.Map;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.graphql.server.test.queries.NoopQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.testing.junit5.Socket;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.graphql.server.GraphQlConstants.GRAPHQL_SCHEMA_URI;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for microprofile-graphql implementation via /graphql endpoint.
 */
@HelidonTest
@DisableDiscovery
@AddExtension(GraphQlCdiExtension.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddBean(NoopQueriesAndMutations.class)
public class GraphQLEndpointIT
        extends AbstractGraphQLEndpointIT {

    @BeforeAll
    public static void setup() throws IOException {
        setupIndex(Person.class);
    }

    @Inject
    public GraphQLEndpointIT(@Socket("@default") String uri) {
        super(uri);
    }

    @Test
    public void basicEndpointTests() {
        // test /graphql endpoint
        Map<String, Object> mapRequest = generateJsonRequest(QUERY_INTROSPECT, null, null);

        // test POST
        Response response = target().request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(JsonUtils.convertMapToJson(mapRequest)));
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

        Map<String, Object> graphQLResults = getJsonResponse(response);
        System.err.println(JsonUtils.convertMapToJson(graphQLResults));
        assertThat(graphQLResults.size(), CoreMatchers.is(1));

        // test GET
        response = target()
                .queryParam(QUERY, encode((String) mapRequest.get(QUERY)))
                .queryParam(OPERATION, encode((String) mapRequest.get(OPERATION)))
                .queryParam(VARIABLES, encode((String) mapRequest.get(VARIABLES)))
                .request(MediaType.APPLICATION_JSON_TYPE).get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        graphQLResults = getJsonResponse(response);
        System.err.println(JsonUtils.convertMapToJson(graphQLResults));
        assertThat(graphQLResults.size(), CoreMatchers.is(1));
    }

    @Test
    public void testGetSchema() {
        WebTarget webTarget = target().path(GRAPHQL_SCHEMA_URI);
        Response response = webTarget.request(MediaType.TEXT_PLAIN).get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }
}
