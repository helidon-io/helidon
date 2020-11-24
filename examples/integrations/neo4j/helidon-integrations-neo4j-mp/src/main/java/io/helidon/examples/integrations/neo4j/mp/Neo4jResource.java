/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.neo4j.mp;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/movies")
@RequestScoped
public class Neo4jResource {
    private static final Jsonb JSONB = JsonbBuilder.create();

    /**
     * The greeting message provider.
     */
    private final Neo4jDataProvider neo4jDataProvider;

    /**
     * Constructor.
     *
     * @param neo4jDataProvider
     */
    @Inject
    public Neo4jResource(Neo4jDataProvider neo4jDataProvider) {
        this.neo4jDataProvider = neo4jDataProvider;
    }

    /**
     * All movies.
     *
     * @return json String with all movies
     */
    @SuppressWarnings("checkstyle:designforextension")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllMovies() {
        String json = JSONB.toJson(neo4jDataProvider.getAllMovies());
        return json;
    }

}

