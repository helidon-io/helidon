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
 *
 */
package io.helidon.examples.integrations.neo4j.se.domain;

import java.util.List;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;


/**
 * The Movie repository.
 */
public final class MovieRepository {

    private final Driver driver;

    /**
     * Constructor for the repo.
     *
     * @param driver
     */
    public MovieRepository(Driver driver) {
        this.driver = driver;
    }

    /**
     * Returns all the movies.
     * @return List with movies
     */
    public List<Movie> findAll(){

        try (var session = driver.session()) {

            var query = ""
                    + "match (m:Movie) "
                    + "match (m) <- [:DIRECTED] - (d:Person) "
                    + "match (m) <- [r:ACTED_IN] - (a:Person) "
                    + "return m, collect(d) as directors, collect({name:a.name, roles: r.roles}) as actors";

            return session.readTransaction(tx -> tx.run(query).list(r -> {
                var movieNode = r.get("m").asNode();

                var directors = r.get("directors").asList(v -> {
                    var personNode = v.asNode();
                    return new Person(personNode.get("born").asInt(), personNode.get("name").asString());
                });

                var actors = r.get("actors").asList(v -> {
                    return new Actor(v.get("name").asString(), v.get("roles").asList(Value::asString));
                });

                var m = new Movie(movieNode.get("title").asString(), movieNode.get("tagline").asString());
                m.setReleased(movieNode.get("released").asInt());
                m.setDirectorss(directors);
                m.setActors(actors);
                return m;
            }));
        }
    }
}
