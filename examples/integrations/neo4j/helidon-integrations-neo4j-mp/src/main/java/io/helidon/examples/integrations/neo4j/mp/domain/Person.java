package io.helidon.examples.integrations.neo4j.mp.domain;

/**
 * author Mark Angrish
 * author Michael J. Simons
 */
public class Person {

    private final String name;

    private Integer born;

    /**
     * Person constructor.
     *
     * @param born
     * @param name
     */
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

    @SuppressWarnings("checkstyle:OperatorWrap")
    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", born=" + born +
                '}';
    }
}
