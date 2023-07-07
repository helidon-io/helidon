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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.dbclient.mongodb.MongoDbClientProvider;
import io.helidon.dbclient.spi.DbClientProvider;

/**
 * Helidon DB Client Mongo support.
 */
@Feature(value = "mongo",
        description = "DB Client with mongo driver",
        in = HelidonFlavor.SE,
        path = {"DbClient", "mongo"}
)
module io.helidon.dbclient.mongodb {
    requires static io.helidon.common.features.api;

    requires java.sql;

    requires transitive jakarta.json;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;
    requires transitive io.helidon.dbclient;
    requires org.mongodb.driver.sync.client;

    exports io.helidon.dbclient.mongodb;
    provides DbClientProvider with MongoDbClientProvider;
}
