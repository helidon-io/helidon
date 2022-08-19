/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import io.helidon.reactive.dbclient.mongodb.MongoDbClientProvider;
import io.helidon.reactive.dbclient.spi.DbClientProvider;

/**
 * Helidon Common Mapper.
 */
module io.helidon.reactive.dbclient.mongodb {
    requires java.logging;
    requires java.sql;

    requires transitive jakarta.json;
    requires mongodb.driver.reactivestreams;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;
    requires org.mongodb.driver.async.client;
    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.reactive.dbclient;
    requires transitive io.helidon.reactive.dbclient.common;
    requires org.reactivestreams;

    exports io.helidon.reactive.dbclient.mongodb;
    provides DbClientProvider with MongoDbClientProvider;
}
