/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbExecute;
import io.helidon.tests.integration.dbclient.common.model.Pokemons;
import io.helidon.tests.integration.dbclient.common.model.Types;

/**
 * Database helper.
 */
public class DBHelper {

    private DBHelper() {
        // cannot be instantiated
    }

    /**
     * Create the schema.
     *
     * @param db db client
     */
    public static void createSchema(DbClient db) {
        try {
            DbExecute exec = db.execute();
            exec.namedDml("create-types");
            exec.namedDml("create-pokemons");
            exec.namedDml("create-poketypes");
        } catch (DbClientException ex) {
            ex.printStackTrace(System.err);
        }
    }

    /**
     * Insert the default data.
     *
     * @param db db client
     */
    public static void insertDataSet(DbClient db) {
        try {
            DbExecute exec = db.execute();
            Types.ALL.forEach(t -> exec.namedInsert("insert-type", t.id(), t.name()));
            Pokemons.ALL.forEach(p -> exec.namedInsert("insert-pokemon", p.id(), p.name(), p.healthy()));
            Pokemons.ALL.forEach(p -> p.types().forEach(t -> exec.namedInsert("insert-poketype", p.id(), t.id())));
        } catch (DbClientException ex) {
            ex.printStackTrace(System.err);
        }
    }
}
