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

package io.helidon.integrations.micronaut.cdi.data;

import java.sql.Connection;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolationException;
import javax.validation.constraints.Pattern;

import io.helidon.integrations.micronaut.cdi.data.app.DbOwnerRepository;
import io.helidon.integrations.micronaut.cdi.data.app.DbPetRepository;
import io.helidon.integrations.micronaut.cdi.data.app.Owner;
import io.helidon.integrations.micronaut.cdi.data.app.Pet;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.present;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest
@Configuration(configSources = "in-mem-h2.properties")
@AddBean(MicronautDataCdiExtensionTest.MyBean.class)
@AddBean(MicronautDataCdiExtensionTest.CdiOnly.class)
class MicronautDataCdiExtensionTest {
    @Inject
    private DbOwnerRepository ownerRepository;
    @Inject
    private DbPetRepository petRepository;
    @Inject
    private MyBean myBean;

    @Test
    void testPet() {
        Optional<Pet> dinoOptional = petRepository.findByName("Dino");
        assertThat(dinoOptional, is(present()));

        Pet pet = dinoOptional.get();
        assertThat(pet.getName(), is("Dino"));
        Owner owner = pet.getOwner();
        assertThat(owner.getName(), is("Fred"));
        assertThat(owner.getAge(), is(45));
    }

    @Test
    void testOwner() {
        Optional<Owner> maybeBarney = ownerRepository.findByName("Barney");
        assertThat(maybeBarney, is(present()));

        Owner barney = maybeBarney.get();
        assertThat(barney.getName(), is("Barney"));
        assertThat(barney.getAge(), is(40));
    }

    @Test
    void testTransaction() {
        assertThat(myBean.getOwner("Hoppy"), is("Barney"));
    }

    @Test
    void testBeanValidation() {
        assertThrows(ConstraintViolationException.class, () -> myBean.getOwner("wrong name"), "Name should not contain spaces");
    }

    public static class CdiOnly {
        private final String message;

        @Inject
        CdiOnly(@ConfigProperty(name = "test.message") String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }

    @ApplicationScoped
    public static class MyBean {
        @Inject
        private DbPetRepository petRepository;
        @Inject
        private Connection connection;
        @Inject
        CdiOnly cdiOnly;

        @Transactional
        public String getOwner(@Pattern(regexp = "\\w+") String pet) {
            assertThat(connection, notNullValue());
            assertThat(cdiOnly.message(), is("Hello"));
            return petRepository.findByName(pet)
                    .map(Pet::getOwner)
                    .map(Owner::getName)
                    .orElse(null);
        }
    }
}