/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.helidon.examples.integrations.neo4j.mp.domain.Movie;
import io.helidon.examples.integrations.neo4j.mp.domain.MovieRepository;

/**
 * REST endpoint for movies.
 */
@Path("/movies")
@RequestScoped
public class Neo4jResource {
    /**
     * The greeting message provider.
     */
    private final MovieRepository movieRepository;

    /**
     * Constructor.
     *
     * @param movieRepository
     */
    @Inject
    public Neo4jResource(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /**
     * All movies.
     *
     * @return json String with all movies
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

}

