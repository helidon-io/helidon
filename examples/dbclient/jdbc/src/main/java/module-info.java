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
 * JDBC example.
 */
module io.helidon.examples.dbclient.jdbc {
    requires java.logging;

    requires io.helidon.config;
    requires io.helidon.dbclient.health;
    requires io.helidon.health;
    requires io.helidon.media.jsonb;
    requires io.helidon.media.jsonp;
    requires io.helidon.metrics;
    requires io.helidon.tracing;
    requires io.helidon.dbclient.blocking;
    requires io.helidon.webserver;
    requires io.helidon.dbclient;

    requires io.helidon.examples.dbclient.common;
}
