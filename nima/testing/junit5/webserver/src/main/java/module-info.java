/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 * Unit and integration testing support for Níma WebServer and JUnit 5.
 *
 * @see io.helidon.nima.testing.junit5.webserver.ServerTest
 * @see io.helidon.nima.testing.junit5.webserver.RoutingTest
 */
module io.helidon.nima.testing.junit5.webserver {
    requires transitive io.helidon.common.testing.http.junit5;
    requires transitive io.helidon.nima.webserver;
    requires transitive io.helidon.nima.webclient;
    requires transitive org.junit.jupiter.api;
    requires transitive hamcrest.all;

    exports io.helidon.nima.testing.junit5.webserver;
}