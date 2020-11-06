/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.graphql.server.GraphQLResource.SCHEMA_URL;
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
@AddBean(GraphQLResource.class)
@AddBean(GraphQLApplication.class)
@AddConfig(key = "server.static.classpath.context", value = "/ui")
@AddConfig(key = "server.static.classpath.location", value = "/web")
public class GraphQLEndpointIT
        extends AbstractGraphQLEndpointIT {

    @BeforeAll
    public static void setup() throws IOException {
        _startupTest(Person.class);
    }

    @Test
    public void basicEndpointTests() {
        // test /graphql endpoint
        WebTarget webTarget = getGraphQLWebTarget().path(GRAPHQL);
        Map<String, Object> mapRequest = generateJsonRequest(QUERY_INTROSPECT, null, null);

        // test POST
        Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(JsonUtils.convertMapToJson(mapRequest)));
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

        Map<String, Object> graphQLResults = getJsonResponse(response);
        System.err.println(JsonUtils.convertMapToJson(graphQLResults));
        assertThat(graphQLResults.size(), CoreMatchers.is(1));

        // test GET
        webTarget = getGraphQLWebTarget().path(GRAPHQL)
                .queryParam(QUERY, encode((String) mapRequest.get(QUERY)))
                .queryParam(OPERATION, encode((String) mapRequest.get(OPERATION)))
                .queryParam(VARIABLES, encode((String) mapRequest.get(VARIABLES)));

        response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        graphQLResults = getJsonResponse(response);
        System.err.println(JsonUtils.convertMapToJson(graphQLResults));
        assertThat(graphQLResults.size(), CoreMatchers.is(1));
    }

    @Test
    public void testUIEndpoint() {
        WebTarget webTarget = getGraphQLWebTarget().path(UI).path("index.html");
        Response response = webTarget.request().get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }

    @Test
    public void testGetSchema() {
        WebTarget webTarget = getGraphQLWebTarget().path(GRAPHQL).path(SCHEMA_URL);
        Response response = webTarget.request(MediaType.TEXT_PLAIN).get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }
}
