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
package io.helidon.docs.se;

import io.helidon.dbclient.DbClient;

@SuppressWarnings("ALL")
class DbClientSnippets {

    void snippet_1(DbClient dbClient) {
        // tag::snippet_1[]
        dbClient.execute()
                .createQuery("SELECT name FROM Pokemons WHERE id = ?")
                .params(1)
                .execute();
        // end::snippet_1[]
    }

    void snippet_2(DbClient dbClient) {
        // tag::snippet_2[]
        dbClient.transaction()
                .createQuery("SELECT name FROM Pokemons WHERE id = :id")
                .addParam("id", 1)
                .execute();
        // end::snippet_2[]
    }

    void snippet_3(DbClient dbClient) {
        // tag::snippet_3[]
        dbClient.execute()
                .createUpdate("""
                              {
                                  "collection": "pokemons","
                                  "value":{$set:{"name":$name}},
                                  "query":{id:$id}
                              }
                              """)
                .addParam("id", 1)
                .addParam("name", "Pikachu")
                .execute();
        // end::snippet_3[]
    }

    void snippet_4(DbClient dbClient) {
        // tag::snippet_4[]
        long count = dbClient.execute()
                .insert("INSERT INTO Pokemons (id, name) VALUES(?, ?)",
                        1, "Pikachu");
        System.out.printf("Inserted %d records\n", count);
        // end::snippet_4[]
    }
}
