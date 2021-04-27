/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Logger;

import io.helidon.common.reactive.Single;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

public class MongoDbClientTest {

    private static final Logger LOGGER = Logger.getLogger(MongoDbClientTest.class.getName());

    private static final MongoClient CLIENT = Mockito.mock(MongoClient.class);
    private static final MongoDatabase DB = Mockito.mock(MongoDatabase.class);

    @Test
    void testUnwrapClientClass() {
        MongoDbClient dbClient = new MongoDbClient(new MongoDbClientProviderBuilder(), CLIENT, DB);
        Single<MongoClient> future = dbClient.unwrap(MongoClient.class);
        MongoClient connection = future.await();
        assertThat(connection, notNullValue());
        Single<MongoDatabase> dbFuture = dbClient.unwrap(MongoDatabase.class);
        MongoDatabase db = dbFuture.await();
        assertThat(db, notNullValue());
    }

    @Test
    void testUnsupportedUnwrapClientClass() {
        MongoDbClient dbClient = new MongoDbClient(new MongoDbClientProviderBuilder(), CLIENT, DB);
        try {
            Single<MongoCollection> future = dbClient.unwrap(MongoCollection.class);
            MongoCollection connection = future.await();
            fail("Unsupported unwrap call must throw UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            LOGGER.fine(() -> String.format("Caught expected UnsupportedOperationException: %s", ex.getMessage()));
        }
    }

    @Test
    void testUnwrapExecutorClass() {
        MongoDbClient dbClient = new MongoDbClient(new MongoDbClientProviderBuilder(), CLIENT, DB);
        dbClient.execute(exec -> {
            Single<MongoDatabase> future = exec.unwrap(MongoDatabase.class);
            MongoDatabase connection = future.await();
            assertThat(connection, notNullValue());
            return exec.query("{\"operation\": \"command\", \"query\": { ping: 1 }}");
        });
    }

    @Test
    void testUnsupportedUnwrapExecutorClass() {
        MongoDbClient dbClient = new MongoDbClient(new MongoDbClientProviderBuilder(), CLIENT, DB);
        dbClient.execute(exec -> {
            try {
                Single<MongoClient> future = exec.unwrap(MongoClient.class);
                MongoClient connection = future.await();
                fail("Unsupported unwrap call must throw UnsupportedOperationException");
            } catch (UnsupportedOperationException ex) {
                LOGGER.fine(() -> String.format("Caught expected UnsupportedOperationException: %s", ex.getMessage()));
            }
            return exec.query("{\"operation\": \"command\", \"query\": { ping: 1 }}");
        });
    }

}
