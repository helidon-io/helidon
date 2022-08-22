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

import io.helidon.reactive.dbclient.spi.DbClientProvider;
import io.helidon.reactive.dbclient.spi.DbClientServiceProvider;
import io.helidon.reactive.dbclient.spi.DbMapperProvider;

/**
 * Helidon reactive DB Client.
 *
 * @see io.helidon.reactive.dbclient.DbClient
 */
module io.helidon.reactive.dbclient {
    requires java.logging;
    requires transitive io.helidon.config;
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.common.mapper;
    requires transitive io.helidon.common.reactive;

    exports io.helidon.reactive.dbclient;
    exports io.helidon.reactive.dbclient.spi;

    uses DbClientProvider;
    uses DbMapperProvider;
    uses DbClientServiceProvider;
}
