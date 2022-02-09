/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.micronaut.data.model;

import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.AutoPopulated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Pet database entity.
 */
@Entity
public class Pet {

    @Id
    @AutoPopulated
    private UUID id;
    private String name;
    @ManyToOne
    private Owner owner;
    private PetType type = PetType.DOG;

    /**
     * Creates a new pet.
     * @param name name of the pet
     * @param owner owner of the pet (optional)
     */
    // NOTE - please use Nullable from this package, jakarta.annotation.Nullable will fail with JPMS,
    // as it is declared in the same package as is used by annother module (jakarta.annotation-api)
    @Creator
    public Pet(String name, @Nullable Owner owner) {
        this.name = name;
        this.owner = owner;
    }

    public Owner getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        this.type = type;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Type of pet.
     */
    public enum PetType {
        /**
         * Dog.
         */
        DOG,
        /**
         * Cat.
         */
        CAT
    }
}
