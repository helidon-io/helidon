/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se.integrations;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.integrations.neo4j.Neo4j;
import io.helidon.integrations.neo4j.health.Neo4jHealthCheck;
import io.helidon.integrations.neo4j.metrics.Neo4jMetricsSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;

@SuppressWarnings("ALL")
class Neo4jSnippets {

    void snippet_1(Config config) {
        // tag::snippet_1[]
        Neo4j neo4j = Neo4j.create(config.get("neo4j"));
        Driver neo4jDriver = neo4j.driver();
        // end::snippet_1[]
    }

    // stub
    record Person(int born, String name) {
    }

    // stub
    record Actor(String name, List<String> roles) {
    }

    // stub
    record Movie(String title, String tagline, int released, List<Person> directors, List<Actor> actors) {
    }

    // tag::snippet_2[]
    record MovieRepository(Driver driver) { // <1>

        List<Movie> findAll() { // <2>
            try (var session = driver.session()) {
                var query = """
                            match (m:Movie)
                            match (m) <- [:DIRECTED] - (d:Person)
                            match (m) <- [r:ACTED_IN] - (a:Person)
                            return m, collect(d) as directors, collect({name:a.name, roles: r.roles}) as actors
                            """;

                return session.readTransaction(tx -> tx.run(query).list(r -> {
                    var movieNode = r.get("m").asNode();

                    var directors = r.get("directors").asList(v -> {
                        var personNode = v.asNode();
                        return new Person(personNode.get("born").asInt(), personNode.get("name").asString());
                    });

                    var actors = r.get("actors").asList(v -> {
                        return new Actor(v.get("name").asString(), v.get("roles").asList(Value::asString));
                    });

                    return new Movie(
                            movieNode.get("title").asString(),
                            movieNode.get("tagline").asString(),
                            movieNode.get("released").asInt(),
                            directors,
                            actors);
                }));
            }
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    record MovieService(MovieRepository movieRepository) implements HttpService {

        @Override
        public void routing(HttpRules rules) {
            rules.get("/api/movies", this::findMoviesHandler);
        }

        void findMoviesHandler(ServerRequest request, ServerResponse response) {
            response.send(this.movieRepository.findAll());
        }
    }
    // end::snippet_3[]

    static void snippet_4(Config config) {
        // tag::snippet_4[]
        Neo4j neo4j = Neo4j.create(config.get("neo4j"));
        Driver driver = neo4j.driver(); // <1>

        Neo4jMetricsSupport.builder()
                .driver(driver)
                .build()
                .initialize(); // <2>

        ObserveFeature observeFeature = ObserveFeature.builder()
                .addObserver(HealthObserver.builder()
                                     .addCheck(Neo4jHealthCheck.create(driver))
                                     .build())
                .build(); // <3>

        WebServer server = WebServer.builder()
                .addFeature(observeFeature)
                .routing(it -> it.register(new MovieService(new MovieRepository(driver)))) // <4>
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/api/movies");
        // end::snippet_4[]
    }

    void snippet_5(Driver driver) {
        // tag::snippet_5[]
        Neo4jMetricsSupport.builder()
                .driver(driver)
                .build()
                .initialize();
        // end::snippet_5[]
    }

    void snippet_6(Driver driver) {
        // tag::snippet_6[]
        ObserveFeature observeFeature = ObserveFeature.builder()
                .addObserver(HealthObserver.builder()
                                     .addCheck(Neo4jHealthCheck.create(driver))
                                     .build())
                .build();
        // end::snippet_6[]
    }
}
