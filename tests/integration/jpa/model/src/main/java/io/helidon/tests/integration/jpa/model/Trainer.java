/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.jpa.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;

/**
 * Trainer entity.
 */
@Entity
public class Trainer implements Serializable {

    @Id
    @GeneratedValue
    private int id;

    private String name;

    private int age;

    @OneToMany(mappedBy = "trainer", fetch = FetchType.LAZY)
    private List<Pokemon> pokemons;

    public Trainer() {
    }

    public Trainer(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
    
    public List<Pokemon> getPokemons() {
        return pokemons;
    }

    public void setPokemons(List<Pokemon> pokemons) {
        this.pokemons = pokemons;
    }

    @Override
    public boolean equals(Object oth) {
        if (this == oth) {
            return true;
        }
        if (oth == null) {
            return false;
        }
        if (oth instanceof Trainer) {
            return id == ((Trainer)oth).id
                    && age == ((Trainer)oth).age
                    && Objects.equals(name, ((Trainer)oth).name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = id;
        if (name != null) {
            hashCode = hashCode * 31 + name.hashCode();
        }
        if (age != 0) {
            hashCode = hashCode * 31 + age;
        }
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trainer: {id=");
        sb.append(id);
        sb.append(", name=");
        if (name != null) {
            sb.append('"').append(name).append('"');
        } else {
            sb.append("<null>");
        }
        sb.append(", age=");
        sb.append(age);
        sb.append('}');
        return sb.toString();
    }

}
