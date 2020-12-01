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

package io.helidon.examples.integrations.neo4j.se;

import io.helidon.examples.integrations.neo4j.se.domain.MovieRepository;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * The Movie service.
 *
 * Implements {@link io.helidon.webserver.Service}
 */
public class MovieService implements Service {

    private final MovieRepository movieRepository;

    /**
     * The movies service.
     * @param movieRepository
     */
    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /**
     * Main routing done here.
     *
     * @param rules
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/api/movies", this::findMoviesHandler);
    }

    private void findMoviesHandler(ServerRequest request, ServerResponse response) {
        response.send(this.movieRepository.findAll());
    }
}
