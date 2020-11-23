package io.helidon.examples.integrations.neo4j.se.domain;


import java.util.ArrayList;
import java.util.List;

/**
 * @author Mark Angrish
 * @author Michael J. Simons
 */
public class Movie {

    private final String title;

    private final String description;

    private List<Actor> actors = new ArrayList<>();

    private List<Person> directors = new ArrayList<>();

    private Integer released;

    public Movie(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public List<Actor> getActors() {
        return actors;
    }

    public void setActors(List<Actor> actors) {
        this.actors = actors;
    }

    public String getDescription() {
        return description;
    }

    public List<Person> getDirectors() {
        return directors;
    }

    public void setDirectorss(List<Person> directors) {
        this.directors = directors;
    }

    public Integer getReleased() {
        return released;
    }

    public void setReleased(Integer released) {
        this.released = released;
    }
}