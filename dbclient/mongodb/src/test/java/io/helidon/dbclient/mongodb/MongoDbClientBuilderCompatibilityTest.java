/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MongoDbClientBuilderCompatibilityTest {

    @Test
    void testMongoClientBuilderPublicApiShape() throws Exception {
        Method create = MongoDbClientBuilder.class.getMethod("create");
        Method config = MongoDbClientBuilder.class.getMethod("config", Config.class);
        Method credDb = MongoDbClientBuilder.class.getMethod("credDb", String.class);

        assertThat(create.getReturnType().getName(), is(MongoDbClientBuilder.class.getName()));
        assertThat(config.getReturnType().getName(), is(MongoDbClientBuilder.class.getName()));
        assertThat(credDb.getReturnType().getName(), is(MongoDbClientBuilder.class.getName()));
        assertThat(Modifier.isPublic(create.getModifiers()), is(true));
        assertThat(Modifier.isPublic(config.getModifiers()), is(true));
        assertThat(Modifier.isPublic(credDb.getModifiers()), is(true));
    }

    @Test
    void testMongoClientBuilderConfigAndLazyDbConfig() {
        Config config = Config.builder()
                .addSource(ConfigSources.create(Map.of(
                        "connection.url", "mongodb://localhost/testdb",
                        "connection.username", "scott",
                        "connection.password", "tiger",
                        "credDb", "admin")))
                .build();

        MongoDbClientBuilder builder = new MongoDbClientBuilder()
                .config(config);

        try (MongoDbClient client = (MongoDbClient) builder.build()) {
            assertThat(client.dbType(), is(MongoDbClientProvider.DB_TYPE));
            assertThat(builder.dbConfig().url(), is("mongodb://localhost/testdb"));
            assertThat(builder.dbConfig().username(), is("scott"));
            assertThat(builder.dbConfig().password(), is("tiger"));
            assertThat(builder.dbConfig().credDb(), is("admin"));
        }
    }
}
