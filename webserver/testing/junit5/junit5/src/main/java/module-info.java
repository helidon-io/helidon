/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * Helidon WebServer Testing JUnit5 Support.
 *
 * @see io.helidon.webserver.testing.junit5.ServerTest
 * @see io.helidon.webserver.testing.junit5.RoutingTest
 */
module io.helidon.webserver.testing.junit5 {
    requires transitive io.helidon.common.testing.http.junit5;
    requires transitive io.helidon.webserver;
    requires transitive io.helidon.webclient;
    requires io.helidon.logging.common;

    requires transitive org.junit.jupiter.api;
    requires transitive hamcrest.all;

    exports io.helidon.webserver.testing.junit5;
    exports io.helidon.webserver.testing.junit5.spi;

    uses io.helidon.webserver.testing.junit5.spi.ServerJunitExtension;
    uses io.helidon.webserver.testing.junit5.spi.DirectJunitExtension;

    provides io.helidon.webserver.testing.junit5.spi.ServerJunitExtension
            with io.helidon.webserver.testing.junit5.Http1ServerJunitExtension;

    provides io.helidon.webserver.testing.junit5.spi.DirectJunitExtension
            with io.helidon.webserver.testing.junit5.Http1DirectJunitExtension;

    opens io.helidon.webserver.testing.junit5 to org.junit.platform.commons;
}