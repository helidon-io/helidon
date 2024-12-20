/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.crac.test;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * A Pokemon Type entity. A type is represented by an ID and a name.
 *
 * Pokemon, and Pokemon character names are trademarks of Nintendo.
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
