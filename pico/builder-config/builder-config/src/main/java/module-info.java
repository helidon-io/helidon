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
 * The Helidon Pico Config Builder.
 */
module io.helidon.pico.builder.config {
    requires static jakarta.inject;
//    requires static jakarta.annotation;
    requires io.helidon.pico.builder;
    requires io.helidon.pico.types;
    requires io.helidon.common;
    requires io.helidon.common.config;
    //    requires transitive io.helidon.pico;

    exports io.helidon.pico.builder.config;
    exports io.helidon.pico.builder.config.spi;

    provides io.helidon.pico.builder.config.spi.ConfigResolver
            with io.helidon.pico.builder.config.spi.BasicConfigResolver;
}
