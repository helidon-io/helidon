package io.helidon.examples.integrations.neo4j.se.domain;


/**
 * @author Mark Angrish
 * @author Michael J. Simons
 */
public class Person {

    private final String name;

    private Integer born;

    public Person(Integer born, String name) {
        this.born = born;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Integer getBorn() {
        return born;
    }

    public void setBorn(Integer born) {
        this.born = born;
    }

    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", born=" + born +
                '}';
    }
}