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
 * Pico API / SPI test module.
 */
open module io.helidon.pico.spi.test {
    requires org.junit.jupiter.api;
    requires hamcrest.all;
    requires jakarta.inject;
    requires io.helidon.common;
    requires transitive io.helidon.pico;

    uses io.helidon.pico.PicoServices;

    exports io.helidon.pico.test.testsubjects;
    exports io.helidon.pico.test;

    provides io.helidon.pico.spi.PicoServicesProvider with
            io.helidon.pico.test.testsubjects.PicoServices1Provider,
            io.helidon.pico.test.testsubjects.PicoServices2Provider,
            io.helidon.pico.test.testsubjects.PicoServices3Provider;
}
