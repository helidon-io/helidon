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

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * A Pokemon Type entity. A type is represented by an ID and a name.
 */
@Entity(name = "PokemonType")
@Table(name = "POKEMONTYPE")
@Access(AccessType.FIELD)
@NamedQueries({
        @NamedQuery(name = "getPokemonTypes",
                    query = "SELECT t FROM PokemonType t"),
        @NamedQuery(name = "getPokemonTypeById",
                    query = "SELECT t FROM PokemonType t WHERE t.id = :id")
})
public class PokemonType {

    @Id
    @Column(name = "ID", nullable = false, updatable = false)
    private int id;

    @Basic(optional = false)
    @Column(name = "NAME")
    private String name;

    /**
     * Creates a new type.
     */
    public PokemonType() {
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
}
