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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;

import static io.helidon.graphql.server.GraphQlConstants.GRAPHQL_WEB_CONTEXT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Abstract functionality for integration tests via /graphql endpoint.
 */
public abstract class AbstractGraphQLEndpointIT extends AbstractGraphQLTest {

    private static final Logger LOGGER = Logger.getLogger(AbstractGraphQLEndpointIT.class.getName());

    /**
     * Initial GraphiQL query from UI.
     */
    protected static final String QUERY_INTROSPECT = """
            query {
              __schema {
                types {
                  name
                }
              }
            }""";

    protected static final String QUERY = "query";
    protected static final String VARIABLES = "variables";
    protected static final String OPERATION = "operationName";

    private final WebTarget target;

    @SuppressWarnings("resource")
    public AbstractGraphQLEndpointIT(String uri) {
        this.target = ClientBuilder.newBuilder()
                .register(new LoggingFeature(LOGGER, Level.WARNING, LoggingFeature.Verbosity.PAYLOAD_ANY, 32768))
                .property(ClientProperties.FOLLOW_REDIRECTS, true)
                .build()
                .target(uri)
                .path(GRAPHQL_WEB_CONTEXT);
    }

    /**
     * Create the Jandex index with the supplied classes.
     *
     * @param clazzes classes to add to index
     */
    public static void setupIndex(Class<?>... clazzes) throws IOException {
        // set up the Jandex index with the required classes
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
        String indexFileName = getTempIndexFile();
        setupIndex(indexFileName, clazzes);
        System.setProperty(JandexUtils.PROP_INDEX_FILE, indexFileName);
    }

    /**
     * Get the {@link WebTarget}.
     *
     * @return WebTarget
     */
    protected WebTarget target() {
        return target;
    }

    /**
     * Encode the <code>{</code> and <code>}</code>.
     *
     * @param param string to encode
     * @return an encoded string
     */
    protected String encode(String param) {
        return param == null ? null : param.replaceAll("}", "%7D").replaceAll("\\{", "%7B");
    }

    /**
     * Generate a JSON Map with a request to send to graphql.
     *
     * @param query     the query to send
     * @param operation optional operation
     * @param variables optional variables
     * @return map
     */
    @SuppressWarnings("SameParameterValue")
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
     * @param response {@link jakarta.ws.rs.core.Response} received from web server
     * @return JSON entity as a map
     */
    protected Map<String, Object> getJsonResponse(Response response) {
        String stringResponse = response.readEntity(String.class);
        assertThat(stringResponse, is(notNullValue()));
        return JsonUtils.convertJSONtoMap(stringResponse);
    }
}
