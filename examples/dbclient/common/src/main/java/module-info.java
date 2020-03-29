/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Common classes for Pokemon examples for DB Client.
 */
module io.helidon.examples.dbclient.common {
    requires java.logging;

    requires java.json;
    requires transitive io.helidon.dbclient;
    requires io.helidon.common.http;
    requires transitive io.helidon.webserver;

    exports io.helidon.examples.dbclient.common;

    provides io.helidon.dbclient.spi.DbMapperProvider with io.helidon.examples.dbclient.common.PokemonMapperProvider;
}
