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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import graphql.schema.idl.SchemaPrinter;

/**
 * A resource for servicing GraphQL requests.
 */
@Path("/")
@ApplicationScoped
public class GraphQLResource {

    /**
     * Indicates the path to return the GraphQL schema.
     */
    protected static final String SCHEMA_URL = "schema.graphql";

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(GraphQLResource.class.getName());

    /**
     * Options for {@link SchemaPrinter}.
     */
    private static final SchemaPrinter.Options OPTIONS = SchemaPrinter.Options
            .defaultOptions().includeDirectives(false)
            .includeExtendedScalarTypes(true)
            .includeScalarTypes(true);

    /**
     * {@link ExecutionContext} for this resource.
     */
    private ExecutionContext<String> context;

    /**
     * {@link SchemaPrinter} to retrieve the schema.
     */
    private SchemaPrinter schemaPrinter;

    @Path(SCHEMA_URL)
    @GET
    //  @Produces({ MediaType.TEXT_PLAIN })
    //  @Consumes({ MediaType.TEXT_PLAIN })
    // see https://github.com/eclipse/microprofile-graphql/issues/202
    @Produces("plain/text")
    @Consumes("plain/text")
    public Response getGraphQLSchema() {
        return Response.ok(schemaPrinter.print(context.getGraphQLSchema())).build();
    }

    /**
     * Process a GET request.
     *
     * @param query     query to execute
     * @param operation optional operation name. e.g "query name { fields }"
     * @param variables optional variables
     * @return a {@link Response} object.
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON })
    @Consumes({MediaType.APPLICATION_JSON })
    public Response processGraphQLQueryGET(@QueryParam("query") String query,
                                           @QueryParam("operationName") String operation,
                                           @QueryParam("variables") String variables) {
        Map<String, Object> mapVariables = null;
        if (variables != null) {
            mapVariables = JsonUtils.convertJSONtoMap(variables);
        }
        return processRequest(query, operation, mapVariables);
    }

    /**
     * Process a POST request.
     *
     * @param body body of the request as Json
     * @return a {@link Response} object.
     */
    @POST
    @Produces({MediaType.APPLICATION_JSON })
    @Consumes({MediaType.APPLICATION_JSON })
    public Response processGraphQLQueryPOST(String body) {
        Map<String, Object> json = JsonUtils.convertJSONtoMap(body);
        return processRequest(
                (String) json.get("query"),
                (String) json.get("operationName"),
                getVariables(json.get("variables")));
    }

    /**
     * Initialize the {@link ExecutionContext}.
     */
    @PostConstruct
    public void init() {
        try {
            context = new ExecutionContext<>("Dummy Context");
            schemaPrinter = new SchemaPrinter(OPTIONS);
        } catch (Exception e) {
            LOGGER.warning("Unable to build GraphQL Schema: " + e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Process the GraphQL Query.
     *
     * @param query     query to process
     * @param operation operation name, may be null
     * @param variables variables to apply to query, may be null
     * @return a {@link Response} containing the results of the query
     */
    private Response processRequest(String query, String operation, Map<String, Object> variables) {
        return Response.ok(JsonUtils.convertMapToJson(context.execute(query, operation, variables))).build();
    }

    /**
     * Return a {@link Map} of variables from the given argument.
     *
     * @param variables Json string of variables
     * @return a {@link Map} of variables
     */
    private Map<String, Object> getVariables(Object variables) {
        if (variables instanceof Map) {
            Map<?, ?> inputVars = (Map) variables;
            Map<String, Object> vars = new HashMap<>();

            inputVars.forEach((k, v) -> vars.put(String.valueOf(k), v));
            return vars;
        }
        return JsonUtils.convertJSONtoMap(String.valueOf(variables));
    }
}
