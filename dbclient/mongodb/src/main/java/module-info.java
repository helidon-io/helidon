/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon Database Client MongoDB.
 */
@Features.Name("mongo")
@Features.Description("Database client with mongo driver")
@Features.Path({"DbClient", "mongo"})
@Features.Flavor(HelidonFlavor.SE)
module io.helidon.dbclient.mongodb {

    requires java.sql;
    requires org.mongodb.bson;
    requires org.mongodb.driver.core;
    requires org.mongodb.driver.sync.client;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.dbclient;
    requires transitive jakarta.json;

    exports io.helidon.dbclient.mongodb;

    provides io.helidon.dbclient.spi.DbClientProvider with io.helidon.dbclient.mongodb.MongoDbClientProvider;

}
