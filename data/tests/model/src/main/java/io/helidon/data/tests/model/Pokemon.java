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
package io.helidon.data.tests.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * Tests Model: Pokemon.
 */
@Entity
@Table(name = "POKEMON")
@NamedQuery(name = "Pokemon.deleteTemp", query = "DELETE FROM Pokemon p WHERE p.id >= 100")
public class Pokemon {

    @ManyToOne
    @JoinColumn(name = "TRAINER_ID")
    public Trainer trainer;
    @ManyToMany(targetEntity = Type.class, fetch = FetchType.EAGER)
    @JoinTable(name = "POKEMON_TYPE",
               joinColumns = @JoinColumn(
                       name = "POKEMON_ID",
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
        sb.append(", trainer=");
        sb.append(trainer != null? trainer.toString() : "null");
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

    /**
     * Creates clone of provided {@link Pokemon} instance.
     *
     * @param src source instance to be cloned
     * @return the clone
     */
    public static Pokemon clone(Pokemon src) {
        List<Type> newTypes = new ArrayList<>(src.getTypes().size());
        src.getTypes().forEach(type -> newTypes.addLast(Type.clone(type)));
        return new Pokemon(src.getId(),
                    Trainer.clone(src.getTrainer()),
                    src.getName(),
                    src.getHp(),
                    src.isAlive(),
                    List.copyOf(newTypes));
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
        Set<Type> collectionSet = new HashSet<>(collection.size());
        collectionSet.addAll(collection);
        for (Type type : types) {
            if (collectionSet.contains(type)) {
                collectionSet.remove(type);
            } else {
                return false;
            }
        }
        return collectionSet.isEmpty();
    }

}
