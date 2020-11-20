package io.helidon.examples.integrations.neo4j.mp.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael J. Simons
 */
public class Actor {

    private final String name;

    private final List<String> roles;

    public Actor(String name, final List<String> roles) {
        this.name = name;
        this.roles = new ArrayList<>(roles);
    }

    public String getName() {
        return name;
    }

    public List<String> getRoles() {
        return roles;
    }
}