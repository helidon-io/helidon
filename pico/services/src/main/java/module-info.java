/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * The Pico Runtime Services Module.
 */
module io.helidon.pico.services {
    requires static jakarta.inject;
    requires static jakarta.annotation;
    requires io.helidon.builder;
    requires io.helidon.common.types;
    requires io.helidon.common;
    requires io.helidon.common.config;
    requires transitive io.helidon.pico;

    exports io.helidon.pico.services;

    provides io.helidon.pico.spi.PicoServicesProvider
            with io.helidon.pico.services.DefaultPicoServicesProvider;

    uses io.helidon.pico.Module;
    uses io.helidon.pico.Application;
}
