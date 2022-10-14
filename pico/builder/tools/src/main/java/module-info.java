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
 * The Pico Builder tools module.
 */
module io.helidon.pico.builder.tools {
    requires java.compiler;
    requires io.helidon.pico.types;
    requires io.helidon.pico.builder.api;
    requires io.helidon.pico.builder.spi;
    requires io.helidon.pico.builder.runtime.tools;
    requires io.helidon.common;
    requires io.helidon.config.metadata;

    exports io.helidon.pico.builder.tools;

    provides io.helidon.pico.builder.spi.BuilderCreator with io.helidon.pico.builder.tools.DefaultBuilderCreator;
    provides io.helidon.pico.builder.tools.TypeInfoCreator with io.helidon.pico.builder.tools.BuilderTypeTools;
}
