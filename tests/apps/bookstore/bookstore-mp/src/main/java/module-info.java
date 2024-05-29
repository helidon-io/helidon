/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
 * Helidon MP Bookstore test application
 */
module io.helidon.tests.apps.bookstore.mp {

    requires io.helidon.logging.common;
    requires io.helidon.logging.jul;
    requires io.helidon.microprofile.bundle.core;
    requires io.helidon.microprofile.health;
    requires io.helidon.microprofile.metrics;
    requires io.helidon.tests.apps.bookstore.common;
    requires jakarta.json.bind;
    requires jakarta.json;

    opens io.helidon.tests.apps.bookstore.mp to io.helidon.microprofile.cdi,weld.core.impl;

    exports io.helidon.tests.apps.bookstore.mp;

}
