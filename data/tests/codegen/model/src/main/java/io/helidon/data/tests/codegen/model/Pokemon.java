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
package io.helidon.data.tests.codegen.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@Entity
@Table(name = "POKEMNON")
@NamedQuery(name = "Pokemon.deleteTemp", query = "DELETE FROM Pokemon p WHERE p.id >= 100")
public class Pokemon {

    @ManyToOne
    @JoinColumn(name = "TRAINER_ID")
    public Trainer trainer;
    @ManyToMany(targetEntity = Type.class, fetch = FetchType.EAGER)
    @JoinTable(name = "POKEMNON_TYPE",
               joinColumns = @JoinColumn(
                       name = "POKEMNON_ID",
                       referencedColumnName = "ID"
               ),
               inverseJoinColumns = @JoinColumn(
                       name = "TYPE_ID",
                       referencedColumnName = "ID"
               ))
    public Collection<Type> types;
    @Id
    private int id;
    private String name;
    private int hp;
    private boolean alive;

    public Pokemon() {
        this(-1, null, null, -1, false, Collections.emptyList());
    }

    // Required for testUnionWithMultiselectEntityParametersInSelection
    // to select ResultKind.CONSTRUCTOR for the query.
    public Pokemon(int id, String name) {
        this(id, null, name, 100, true, Collections.emptyList());
    }

    public Pokemon(int id, String name, Collection<Type> types) {
        this(id, null, name, 100, true, types);
    }

    public Pokemon(int id, Trainer trainer, String name, Collection<Type> types) {
        this(id, trainer, name, 100, true, types);
    }

    public Pokemon(int id, Trainer keeper, String name, int hp, boolean alive, Collection<Type> types) {
        this.id = id;
        this.trainer = keeper;
        this.name = name;
        this.hp = hp;
        this.alive = alive;
        this.types = types;
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

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public Collection<Type> getTypes() {
        return types;
    }

    public void setTypes(Collection<Type> types) {
        this.types = types;
    }

    public Trainer getTrainer() {
        return trainer;
    }

    public void setTrainer(Trainer trainer) {
        this.trainer = trainer;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        return id == ((Pokemon) obj).id
                && alive == ((Pokemon) obj).alive
                && Objects.equals(name, ((Pokemon) obj).name)
                && Objects.equals(trainer, ((Pokemon) obj).trainer)
                && typesEquals(((Pokemon) obj).types);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, trainer);
        for (Type type : types) {
            result = 31 * result + type.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append(" {id=");
        sb.append(id);
        sb.append(", name=");
        sb.append(name);
        sb.append(", hp=");
        sb.append(hp);
        sb.append(", alive=");
        sb.append(alive);
        sb.append(", keeper=");
        sb.append(trainer.toString());
        sb.append(", types=[");
        boolean first = true;
        for (Type type : types) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(type.toString());
        }
        sb.append("]}");
        return sb.toString();
    }

    private boolean typesEquals(Collection<Type> collection) {
        if (types == collection) {
            return true;
        } else if (types == null || collection == null) {
            return false;
        }
        if (types.size() != collection.size()) {
            return false;
        }
        Iterator<Type> typeIterator = types.iterator();
        Iterator<Type> collectionIterator = collection.iterator();
        int size = types.size();
        for (int i = 0; i < size; i++) {
            if (typeIterator.hasNext() && collectionIterator.hasNext()) {
                if (!Objects.equals(typeIterator.next(), collectionIterator.next())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }
}
