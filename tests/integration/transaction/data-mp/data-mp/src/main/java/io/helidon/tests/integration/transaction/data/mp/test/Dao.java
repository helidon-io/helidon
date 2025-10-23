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
package io.helidon.tests.integration.transaction.data.mp.test;

import java.util.function.Consumer;

import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class Dao {

    /**
     * Run transaction test as {@link Transactional.TxType#REQUIRED} top level transaction.
     *
     * @param pokemonRepository {@link PokemonRepository} instance
     * @param cdiEm CDI injected EntityManager
     * @param dataTask data repository task
     * @param cdiTask CDI task
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public void requiredDataFirst(PokemonRepository pokemonRepository,
                                  CdiEM cdiEm,
                                  Consumer<PokemonRepository> dataTask,
                                  Consumer<CdiEM> cdiTask) {
        dataTask.accept(pokemonRepository);
        cdiTask.accept(cdiEm);
    }

    /**
     * Run transaction test as {@link Transactional.TxType#REQUIRED} top level transaction.
     *
     * @param pokemonRepository {@link PokemonRepository} instance
     * @param cdiEm CDI injected EntityManager
     * @param dataTask data repository task
     * @param cdiTask CDI task
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public void requiredCdiFirst(PokemonRepository pokemonRepository,
                                  CdiEM cdiEm,
                                  Consumer<PokemonRepository> dataTask,
                                  Consumer<CdiEM> cdiTask) {
        cdiTask.accept(cdiEm);
        dataTask.accept(pokemonRepository);
    }

}
