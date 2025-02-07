/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.*;

/**
 * {@code Pokemon} entity.
 */
@Entity
@SuppressWarnings("JpaDataSourceORMInspection")
public class Pokemon implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private int id;

    @ManyToOne
    @JoinColumn(name = "trainer_id")
    private Trainer trainer;

    @ManyToMany
    @JoinTable(
            name = "pokemon_type",
            joinColumns = @JoinColumn(name = "type_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "pokemon_id", referencedColumnName = "id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"type_id", "pokemon_id"})
    )
    private List<Type> types;

    private String name;

    private int cp;

    public Pokemon() {
    }

    public Pokemon(Trainer trainer, String name, int cp, List<Type> types) {
        this.trainer = trainer;
        this.name = name;
        this.cp = cp;
        this.types = types;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Trainer getTrainer() {
        return trainer;
    }

    public void setTrainer(Trainer trainer) {
        this.trainer = trainer;
    }

    public List<Type> getTypes() {
        return types;
    }

    public void setTypes(List<Type> types) {
        this.types = types;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCp() {
        return cp;
    }

    public void setCp(int cp) {
        this.cp = cp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pokemon pokemon)) {
            return false;
        }
        return id == pokemon.id
               && cp == pokemon.cp
               && Objects.equals(trainer, pokemon.trainer)
               && Objects.equals(name, pokemon.name)
               && Lists.equals(types, pokemon.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, trainer, types, name, cp);
    }

    @Override
    public String toString() {
        return "Pokemon{"
               + "id=" + id
               + ", trainer=" + trainer
               + ", types=" + types
               + ", name='" + name + '\''
               + ", cp=" + cp
               + '}';
    }
}
