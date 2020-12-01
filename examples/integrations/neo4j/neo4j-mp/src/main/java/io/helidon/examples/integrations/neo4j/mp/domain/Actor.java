package io.helidon.examples.integrations.neo4j.mp.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * author Michael J. Simons.
 */
public class Actor {

    private final String name;

    private final List<String> roles;

    /**
     * Constructor.
     * @param name
     * @param roles
     */
    public Actor(String name, final List<String> roles) {
        this.name = name;
        this.roles = new ArrayList<>(roles);
    }
}
