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
 * Helidon Pico ConfigBean Builder Processor module.
 */
module io.helidon.pico.builder.config.processor {
    requires java.compiler;
    requires jakarta.inject;
    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.config.metadata;
    requires io.helidon.builder.processor.tools;
    requires io.helidon.common.types;
    requires io.helidon.pico;
    requires io.helidon.pico.configdriven;
    requires io.helidon.pico.config.services;
    requires transitive io.helidon.builder.config;
    requires transitive io.helidon.builder.processor;
    requires transitive io.helidon.builder.processor.spi;
    requires transitive io.helidon.pico.processor;

    exports io.helidon.pico.configdriven.processor;

    provides javax.annotation.processing.Processor with
            io.helidon.pico.configdriven.processor.ConfiguredByProcessor;
}
