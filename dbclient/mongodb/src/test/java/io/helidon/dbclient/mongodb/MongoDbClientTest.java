/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.lang.System.Logger.Level;

import io.helidon.dbclient.DbExecute;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class MongoDbClientTest {

    private static final System.Logger LOGGER = System.getLogger(MongoDbClientTest.class.getName());
    private static MongoDbClient dbClient;

    @BeforeAll
    static void setup() {
        MongoClient client = Mockito.mock(MongoClient.class);
        MongoDatabase db = Mockito.mock(MongoDatabase.class);
        when(db.runCommand(any())).thenReturn(MongoDbStatement.EMPTY);
        dbClient = new MongoDbClient(new MongoDbClientBuilder(), client, db);
    }

    @Test
    void testUnwrapClientClass() {
        MongoClient connection = dbClient.unwrap(MongoClient.class);
        assertThat(connection, notNullValue());
        MongoDatabase db = dbClient.unwrap(MongoDatabase.class);
        assertThat(db, notNullValue());
    }

    @Test
    void testUnsupportedUnwrapClientClass() {
        try {
            dbClient.unwrap(MongoCollection.class);
            fail("Unsupported unwrap call must throw UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Caught expected UnsupportedOperationException: %s", ex.getMessage()));
        }
    }

    @Test
    void testUnwrapExecutorClass() {
        DbExecute exec = dbClient.execute();
        MongoDatabase connection = exec.unwrap(MongoDatabase.class);
        assertThat(connection, notNullValue());
        exec.query("{\"operation\": \"command\", \"query\": { ping: 1 }}");
    }

    @Test
    void testUnsupportedUnwrapExecutorClass() {
        DbExecute exec = dbClient.execute();
        try {
            exec.unwrap(MongoClient.class);
            fail("Unsupported unwrap call must throw UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Caught expected UnsupportedOperationException: %s"
                    , ex.getMessage()));
        }
        exec.query("{\"operation\": \"command\", \"query\": { ping: 1 }}");
    }

}
