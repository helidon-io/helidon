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
/*
 This class is almost exactly copied from Micronaut examples.
 */
package io.helidon.examples.integrations.micronaut.data;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.helidon.examples.integrations.micronaut.data.model.Owner;
import io.helidon.examples.integrations.micronaut.data.model.Pet;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.event.annotation.EventListener;

/**
 * A Micronaut bean that listens on startup event and populates database with data.
 */
@Singleton
@TypeHint(typeNames = {"oracle.jdbc.OracleDriver"})
public class DbPopulateData {
    private final DbOwnerRepository ownerRepository;
    private final DbPetRepository petRepository;

    @Inject
    DbPopulateData(DbOwnerRepository ownerRepository, DbPetRepository petRepository) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
    }

    @EventListener
    void init(StartupEvent event) {
        Owner fred = new Owner("Fred");
        fred.setAge(45);
        Owner barney = new Owner("Barney");
        barney.setAge(40);
        ownerRepository.saveAll(Arrays.asList(fred, barney));

        Pet dino = new Pet("Dino", fred);
        Pet bp = new Pet("Baby Puss", fred);
        bp.setType(Pet.PetType.CAT);
        Pet hoppy = new Pet("Hoppy", barney);

        petRepository.saveAll(Arrays.asList(dino, bp, hoppy));
    }
}
