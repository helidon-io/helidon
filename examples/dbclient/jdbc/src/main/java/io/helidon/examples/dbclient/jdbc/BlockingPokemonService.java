/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.examples.dbclient.jdbc;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.blocking.BlockingDbClient;
import io.helidon.examples.dbclient.common.AbstractPokemonService;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example service using a database.
 */
public class BlockingPokemonService extends AbstractPokemonService {
    private static final Logger LOGGER = Logger.getLogger(PokemonService.class.getName());

    BlockingPokemonService(DbClient dbClient) {
        super(dbClient);

        // dirty hack to prepare database for our POC
        // MySQL init
        BlockingDbClient blockingDbClient = BlockingDbClient.create(dbClient);
        long result = blockingDbClient.execute(handle -> handle.namedDml("create-table"));
        System.out.println(result);

    }

    /**
     * Delete all pokemons.
     *
     * @param request  the server request
     * @param response the server response
     */
    @Override
    protected void deleteAllPokemons(ServerRequest request, ServerResponse response) {

        try {
            BlockingDbClient blockingDbClient = BlockingDbClient.create(dbClient());

            long count = blockingDbClient.execute(exec -> exec
                    // this is to show how ad-hoc statements can be executed (and their naming in Tracing and Metrics)
                    .createDelete("DELETE FROM pokemons")
                    .execute());
            response.send("Deleted: " + count + " values");
        } catch (Throwable t) {
            sendError(t, response);
        }
    }


}
