/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 * Helidon SE Bookstore test application
 */
module io.helidon.tests.apps.bookstore.se {
    requires java.logging;
    requires java.json;

    requires io.helidon.bundles.webserver;
    requires io.helidon.config.yaml;
    requires io.helidon.config;
    requires io.helidon.health;
    requires io.helidon.health.checks;
    requires io.helidon.metrics;
    requires io.helidon.media.jsonb.server;
    requires io.helidon.media.jackson.server;
    requires io.helidon.tests.apps.bookstore.common;

    exports io.helidon.tests.apps.bookstore.se;
}
