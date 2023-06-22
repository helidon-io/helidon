/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.neo4j;

import io.helidon.examples.integrations.neo4j.domain.MovieRepository;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * The Movie service.
 */
public class MovieService implements HttpService {

    private final MovieRepository movieRepository;

    /**
     * The movies service.
     *
     * @param movieRepository a movie repository.
     */
    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/api/movies", this::findMoviesHandler);
    }

    private void findMoviesHandler(ServerRequest request, ServerResponse response) {
        response.send(this.movieRepository.findAll());
    }
}
