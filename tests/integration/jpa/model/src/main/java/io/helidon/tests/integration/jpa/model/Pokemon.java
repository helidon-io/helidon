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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.UniqueConstraint;

/**
 * Pokemon entity.
 */
@Entity
public class Pokemon implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(Pokemon.class.getName());

    @Id
    @GeneratedValue
    private int id;

    @ManyToOne
    @JoinColumn(name = "trainer_id")
    private Trainer trainer;

    @ManyToMany
    @JoinTable(
        name="pokemon_type",
        joinColumns=@JoinColumn(name="type_id", referencedColumnName="id"),
        inverseJoinColumns=@JoinColumn(name="pokemon_id", referencedColumnName="id"),
        uniqueConstraints=@UniqueConstraint(columnNames={"type_id", "pokemon_id"})
    )
    private List<Type> types;

    private String name;

    private int cp;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pokemon: {id=");
        sb.append(id);
        sb.append(", name=");
        if (name != null) {
            sb.append('"').append(name).append('"');
        } else {
            sb.append("<null>");
        }
        sb.append(", cp=");
        sb.append(cp);
        sb.append(", trainer=");
        sb.append(trainer != null ? trainer.toString() : "<null>");
        sb.append(", types=");
        sb.append(types != null ? types.toString() : "<null>");
        return sb.toString();
    }

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
    public boolean equals(Object oth) {
        if (this == oth) {
            return true;
        }
        if (oth == null) {
            return false;
        }
        if (oth instanceof Pokemon) {
            return id == ((Pokemon)oth).id
                    && cp == ((Pokemon)oth).cp
                    && Objects.equals(name, ((Pokemon)oth).name)
                    && Objects.equals(trainer, ((Pokemon)oth).trainer)
                    && listEquals(((Pokemon)oth).types);
        }
        LOGGER.warning(() -> String.format("Pokemon instanceof failed: %s", oth.getClass().getName()));
        return false;
    }

    private boolean listEquals(List<Type> other) {
        if (this.types == other) {
            return true;
        }
        if (this.types == null && other != null
                || this.types != null && other == null
                || this.types.size() != other.size()) {
            return false;
        }
        Iterator<Type> otherIterator = other.iterator();
        for (Type thisType : this.types) {
            Type otherType = otherIterator.next();
            if ( (thisType != otherType) && ( thisType == null || !thisType.equals(otherType)) ) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = id;
        if (name != null) {
            hashCode = hashCode * 31 + name.hashCode();
        }
        if (trainer != null) {
            hashCode = hashCode * 31 + trainer.hashCode();
        }
        if (cp != 0) {
            hashCode = hashCode * 31 + cp;
        }
        if (types != null) {
            for (Type type : types) {
                hashCode = hashCode * 31 + type.hashCode();
            }
        }
        return hashCode;
    }

}
