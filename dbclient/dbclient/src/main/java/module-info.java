/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.Preview;

/**
 * Helidon Database Client.
 *
 * @see io.helidon.dbclient.DbClient
 */
@Preview
@Feature(value = "Database Client",
         description = "Database Client API",
         in = HelidonFlavor.SE,
         path = "DbClient"
)
module io.helidon.dbclient {


    requires java.sql;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.config;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.common.mapper;
    requires transitive io.helidon.common;

    exports io.helidon.dbclient;
    exports io.helidon.dbclient.spi;

    uses io.helidon.dbclient.spi.DbClientProvider;
    uses io.helidon.dbclient.spi.DbClientServiceProvider;
    uses io.helidon.dbclient.spi.DbMapperProvider;

    provides io.helidon.common.mapper.spi.MapperProvider with io.helidon.dbclient.DbMapperProviderImpl;

}