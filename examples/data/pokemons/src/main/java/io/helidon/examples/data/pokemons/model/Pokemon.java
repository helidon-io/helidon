/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.data.pokemons.model;

import jakarta.persistence.*;

/**
 * Pokemon entity.
 */
// ResultSet mapping of [PokemonRepository]::pokemonByTypeAndName3 method query
@SqlResultSetMapping(
        name = "PokemonByTypeAndNameRSMapping",
        entities = {
                @EntityResult(
                        entityClass = Type.class,
                        fields = {
                                @FieldResult(name = "id", column = "t.ID"),
                                @FieldResult(name = "name", column = "t.NAME")
                        }
                ),
                @EntityResult(
                        entityClass = Pokemon.class,
                        fields = {
                                @FieldResult(name = "id", column = "p.ID"),
                                @FieldResult(name = "name", column = "p.NAME"),
                                @FieldResult(name = "type", column = "p.ID_TYPE")
                        }
                )
})
@Entity
@Table(name = "POKEMON")
public class Pokemon {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(name = "NAME")
    private String name;
    @ManyToOne()
    @JoinColumn(name = "idType")
    @Column(name = "ID_TYPE")
    private Type type;

    /**
     * Create empty Pokemon with all values set to {@code null}.
     */
    public Pokemon() {
        this.id = -1;
        this.name = null;
        this.type = null;
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

    public Type getType() {
        return type;
    }

    public void setType(Type idType) {
        this.type = type;
    }

}
