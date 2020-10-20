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
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.cdi.Main;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.jupiter.api.AfterAll;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Abstract functionality for integration tests via /graphql endpoint.
 */
public abstract class AbstractGraphQLEndpointIT
        extends AbstractGraphQLTest {

    private static final Logger LOGGER = Logger.getLogger(AbstractGraphQLEndpointIT.class.getName());

    /**
     * Initial GraphiQL query from UI.
     */
    protected static final String QUERY_INTROSPECT = "query {\n"
            + "  __schema {\n"
            + "    types {\n"
            + "      name\n"
            + "    }\n"
            + "  }\n"
            + "}";

    protected static final String QUERY = "query";
    protected static final String VARIABLES = "variables";
    protected static final String OPERATION = "operationName";
    protected static final String GRAPHQL = "graphql";
    protected static final String UI = "ui";

    private static String graphQLUrl;

    private static Client client;

    public static Client getClient() {
        return client;
    }

    /**
     * Startup the test and create the Jandex index with the supplied {@link Class}es.
     *
     * @param clazzes {@link Class}es to add to index
     */
    public static void _startupTest(Class<?>... clazzes) throws IOException {
        // setup the Jandex index with the required classes
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
        String indexFileName = getTempIndexFile();
        setupIndex(indexFileName, clazzes);
        System.setProperty(JandexUtils.PROP_INDEX_FILE, indexFileName);

        Main.main(new String[0]);

        ServerCdiExtension current = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class);

        graphQLUrl= "http://127.0.0.1:" + current.port() + "/";
        
        System.out.println("GraphQL URL: " + graphQLUrl);

        client = ClientBuilder.newBuilder()
                .register(new LoggingFeature(LOGGER, Level.WARNING, LoggingFeature.Verbosity.PAYLOAD_ANY, 32768))
                .property(ClientProperties.FOLLOW_REDIRECTS, true)
                .build();
    }

    @AfterAll
    public static void teardownTest() {
        Main.shutdown();
    }

    /**
     * Return a {@link WebTarget} for the graphQL end point.
     *
     * @return a {@link WebTarget} for the graphQL end point
     */
    protected static WebTarget getGraphQLWebTarget() {
        Client client = getClient();
        return client.target(graphQLUrl);
    }

    /**
     * Encode the { and }.
     * @param param {@link String} to encode
     * @return an encoded @link String}
     */
    protected String encode(String param)  {
        return param == null ? null : param.replaceAll("}", "%7D").replaceAll("\\{", "%7B");
    }

    /**
     * Generate a Json Map with a request to send to graphql
     *
     * @param query     the query to send
     * @param operation optional operation
     * @param variables optional variables
     * @return a {@link java.util.Map}
     */
    protected Map<String, Object> generateJsonRequest(String query, String operation, Map<String, Object> variables) {
        Map<String, Object> map = new HashMap<>();
        map.put(QUERY, query);
        map.put(OPERATION, operation);
        map.put(VARIABLES, variables);

        return map;
    }

    /**
     * Return the response as Json.
     *
     * @param response {@link javax.ws.rs.core.Response} received from web server
     * @return the response as Json
     */
    protected Map<String, Object> getJsonResponse(Response response) {
        String stringResponse = (response.readEntity(String.class));
        assertThat(stringResponse, is(notNullValue()));
        return JsonUtils.convertJSONtoMap(stringResponse);
    }
}
