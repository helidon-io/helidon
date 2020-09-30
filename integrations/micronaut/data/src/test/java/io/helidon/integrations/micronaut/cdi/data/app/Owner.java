package io.helidon.integrations.micronaut.cdi.data.app;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import io.micronaut.core.annotation.Creator;

@Entity
public class Owner {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private int age;

    @Creator
    public Owner(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}