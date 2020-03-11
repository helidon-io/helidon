/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.util.JsonUtils;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration tests for microprofile-graphql implementation.
 */
public class GraphQLIT extends AbstractGraphQLIT {

    @BeforeAll
    public static void setup() throws IOException {
        _startupTest(Person.class);
    }

    @Test
    public void basicEndpointTests() throws UnsupportedEncodingException {
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
    public void testUIEndpoint() throws InterruptedException {
        // test /ui endpoint
        WebTarget webTarget = getGraphQLWebTarget().path(UI).path("index.html");
        System.err.println(webTarget.getUri());

        Response response = webTarget.request().get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    }
}
