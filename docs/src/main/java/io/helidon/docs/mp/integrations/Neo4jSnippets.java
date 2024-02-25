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
package io.helidon.docs.mp.integrations;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;

@SuppressWarnings("ALL")
class Neo4jSnippets {

    // stub
    record Person(int born, String name) {
    }

    // stub
    record Actor(String name, List<String> roles) {
    }

    // stub
    record Movie(String title, String tagline, int released, List<Person> directors, List<Actor> actors) {
    }

    // stub
    class MovieRepository {
        List<Movie> findAll() {
            return List.of();
        }
    }

    class Snippet1 {

        class MovieRepository {

            Driver driver;

            // tag::snippet_1[]
            @Inject
            public MovieRepository(Driver driver) {
                this.driver = driver;
            }
            // end::snippet_1[]
        }
    }

    class Snippet2 {

        // tag::snippet_2[]
        @ApplicationScoped
        public class MovieRepository {

            private final Driver driver;

            @Inject
            public MovieRepository(Driver driver) { // <1>
                this.driver = driver;
            }

            List<Movie> findAll() { // <2>
                try (var session = driver.session()) {
                    var query = """
                                match (m:Movie)
                                match (m) <- [:DIRECTED] - (d:Person)
                                match (m) <- [r:ACTED_IN] - (a:Person)
                                return m, collect(d) as directors, collect({name:a.name, roles: r.roles}) as actors
                                """;

                    return session.executeRead(tx -> tx.run(query).list(r -> {
                        var movieNode = r.get("m").asNode();

                        var directors = r.get("directors").asList(v -> {
                            var personNode = v.asNode();
                            return new Person(personNode.get("born").asInt(), personNode.get("name").asString());
                        });

                        var actors = r.get("actors").asList(v -> {
                            return new Actor(v.get("name").asString(), v.get("roles").asList(
                                    Value::asString));
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
    }

    class Snippet3 {

        MovieRepository movieRepository;

        // tag::snippet_3[]
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public List<Movie> getAllMovies() {
            return movieRepository.findAll();
        }
        // end::snippet_3[]
    }
}
