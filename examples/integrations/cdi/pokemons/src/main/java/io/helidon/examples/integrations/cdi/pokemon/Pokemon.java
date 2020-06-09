/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.cdi.pokemon;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * A Pokemon entity class. A Pokemon is represented as a triple of an
 * ID, a name and a type.
 */
@Entity(name = "Pokemon")
@Table(name = "POKEMON")
@Access(AccessType.PROPERTY)
@NamedQueries({
        @NamedQuery(name = "getPokemons",
                    query = "SELECT p FROM Pokemon p"),
        @NamedQuery(name = "getPokemonByName",
                    query = "SELECT p FROM Pokemon p WHERE p.name = :name")
})
public class Pokemon {

    private int id;

    private String name;

    @JsonbTransient
    private PokemonType pokemonType;

    private int type;

    /**
     * Creates a new pokemon.
     */
    public Pokemon() {
    }

    @Id
    @Column(name = "ID", nullable = false, updatable = false)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Basic(optional = false)
    @Column(name = "NAME", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns pokemon's type.
     *
     * @return Pokemon's type.
     */
    @ManyToOne
    public PokemonType getPokemonType() {
        return pokemonType;
    }

    /**
     * Sets pokemon's type.
     *
     * @param pokemonType Pokemon's type.
     */
    public void setPokemonType(PokemonType pokemonType) {
        this.pokemonType = pokemonType;
        this.type = pokemonType.getId();
    }

    @Transient
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
