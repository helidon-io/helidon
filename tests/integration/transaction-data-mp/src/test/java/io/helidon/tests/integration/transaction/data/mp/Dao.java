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
package io.helidon.tests.integration.transaction.data.mp;

import io.helidon.service.registry.Service;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;
import io.helidon.transaction.Tx;

// Dao for transaction tests
// Must be service to allow interceptors being called
@Service.Singleton
class Dao {

    @Tx.Required
    void required(PokemonRepository pokemonRepository, CdiEM cdiEm) {
        cdiTask(cdiEm);
        dataTask(pokemonRepository);
        TestTransaction.dataVerification(pokemonRepository, cdiEm);
    }

    @Tx.New
    void dataTask(PokemonRepository pokemonRepository) {
        TestTransaction.dataTask(pokemonRepository);
    }

    @Tx.New
    void cdiTask(CdiEM cdiEm) {
        TestTransaction.cdiEmTask(cdiEm);
    }

}
