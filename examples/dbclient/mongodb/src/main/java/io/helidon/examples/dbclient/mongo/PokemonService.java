/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.dbclient.mongo;

import io.helidon.dbclient.DbClient;
import io.helidon.examples.dbclient.common.AbstractPokemonService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * A simple service to greet you. Examples:
 * <p>
 * Get default greeting message:
 * curl -X GET {@code http://localhost:8080/greet}
 * <p>
 * Get greeting message for Joe:
 * curl -X GET {@code http://localhost:8080/greet/Joe}
 * <p>
 * Change greeting
 * curl -X PUT {@code http://localhost:8080/greet/greeting/Hola}
 * <p>
 * The message is returned as a JSON object
 */

public class PokemonService extends AbstractPokemonService {

    PokemonService(DbClient dbClient) {
        super(dbClient);
    }

    @Override
    protected void deleteAllPokemons(ServerRequest req, ServerResponse res) {
        long count = dbClient().execute().createNamedDelete("delete-all")
                .execute();
        res.send("Deleted: " + count + " values");
    }
}
