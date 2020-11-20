package io.helidon.examples.integrations.neo4j.mp.domain;

import java.util.List;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;

public final class MovieRepository {

    private final Driver driver;

    public MovieRepository(Driver driver) {
        this.driver = driver;
    }

    public List<Movie> findAll() {

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