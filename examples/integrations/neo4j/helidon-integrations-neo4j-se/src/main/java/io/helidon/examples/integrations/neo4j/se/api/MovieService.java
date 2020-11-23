package io.helidon.examples.integrations.neo4j.se.api;


import io.helidon.examples.integrations.neo4j.se.domain.MovieRepository;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

public class MovieService implements Service {

    private final MovieRepository movieRepository;

    public MovieService(final MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/api/movies", this::findMoviesHandler);
    }

    private void findMoviesHandler(ServerRequest request, ServerResponse response) {
        var movies = this.movieRepository.findAll();
        response.send(movies);
    }
}