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

/**
 * Helidon DB Client.
 *
 * @see io.helidon.dbclient.DbClient
 */
module io.helidon.dbclient {
    requires java.logging;
    requires transitive io.helidon.config;
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.common.mapper;
    requires transitive io.helidon.common.serviceloader;

    exports io.helidon.dbclient;
    exports io.helidon.dbclient.spi;

    uses io.helidon.dbclient.spi.DbClientProvider;
    uses io.helidon.dbclient.spi.DbMapperProvider;
    uses io.helidon.dbclient.spi.DbClientServiceProvider;
}
