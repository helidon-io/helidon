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
package io.helidon.data.tests.codegen.common;

import java.util.Optional;

import io.helidon.data.DataConfig;
import io.helidon.data.ProviderConfig;
import io.helidon.data.jakarta.persistence.DataJpaConfig;
import io.helidon.data.tests.codegen.application.ApplicationData;
import io.helidon.data.tests.codegen.model.Pokemon;
import io.helidon.data.tests.codegen.repository.PokemonRepository;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class TestApplication {

    private static final System.Logger LOGGER = System.getLogger(TestApplication.class.getName());

    // Verify full Data instance cycle from application context
    @Test
    void testDataInitialization(DataConfig dataConfig) throws Exception {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testDataInitialization");
        // Turn off DDL drop-and-create
        DataConfig.Builder builder = DataConfig.builder()
                .name(dataConfig.name());

        ProviderConfig provider = dataConfig.provider();
        if (provider instanceof DataJpaConfig jakartaConfig) {
            // this is the expected one
            builder.provider(DataJpaConfig.builder()
                                     .from(jakartaConfig)
                                     .putProperty("jakarta.persistence.schema-generation.database.action", "none")
                                     .build());
        }
        DataConfig newDataConfig = builder.build();

        ApplicationData applicationData = new ApplicationData(newDataConfig);
        MatcherAssert.assertThat(applicationData.data(), notNullValue());
        io.helidon.data.DataRegistry data = applicationData.data();
        PokemonRepository pokemonRepository = data.repository(PokemonRepository.class);
        Optional<Pokemon> pokemon = pokemonRepository.findById(InitialData.POKEMONS[1].getId());
        assertThat(pokemon.isPresent(), is(true));
        assertThat(pokemon.get(), is(InitialData.POKEMONS[1]));
        data.close();
    }

}
