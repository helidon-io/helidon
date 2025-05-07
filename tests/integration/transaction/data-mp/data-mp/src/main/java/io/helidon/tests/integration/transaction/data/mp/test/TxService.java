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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.service.registry.Service;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

@Service.Singleton
public class TxService {

    private static final System.Logger LOGGER = System.getLogger(TxService.class.getName());

    /**
     * Run transaction test as {@link Tx.Required} top level transaction
     * with {@link Tx.New} transactions for CDI and data repository tasks.
     *
     * @param pokemonRepository {@link PokemonRepository} instance
     * @param cdiEm CDI injected EntityManager
     * @param dataTask data repository task
     * @param cdiTask CDI task
     * @param verification data verification task
     */
    @Tx.Required
    public void required(PokemonRepository pokemonRepository,
                         CdiEM cdiEm,
                         Consumer<PokemonRepository> dataTask,
                         Consumer<CdiEM> cdiTask,
                         BiConsumer<PokemonRepository, CdiEM> verification) {
        try {
            dataTask(pokemonRepository, dataTask);
        } catch (TxException e) {
            LOGGER.log(System.Logger.Level.DEBUG, String.format("TxException in data task: %s", e.getMessage()));
        }

        try {
            cdiTask(cdiEm, cdiTask);
        } catch (TxException e) {
            LOGGER.log(System.Logger.Level.DEBUG, String.format("TxException in CDI task: %s", e.getMessage()));
        }

        verification.accept(pokemonRepository, cdiEm);
    }

    /**
     * Run data repository sub-task as {@link Tx.New} transaction.
     *
     * @param pokemonRepository {@link PokemonRepository} instance
     * @param dataTask data repository task
     */
    @Tx.New
    void dataTask(PokemonRepository pokemonRepository, Consumer<PokemonRepository> dataTask) {
        dataTask.accept(pokemonRepository);
    }

    /**
     * Run CDI sub-task as {@link Tx.New} transaction.
     *
     * @param cdiEm CDI injected EntityManager
     * @param cdiTask CDI task
     */
    @Tx.New
    void cdiTask(CdiEM cdiEm, Consumer<CdiEM> cdiTask) {
        cdiTask.accept(cdiEm);
    }

}
