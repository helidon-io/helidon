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
 * The Builder Processor Tools module.
 */
module io.helidon.builder.processor.tools {
    requires java.compiler;
    requires io.helidon.builder.types;
    requires io.helidon.builder;
    requires io.helidon.builder.processor.spi;
    requires io.helidon.common;
    requires io.helidon.config.metadata;

    exports io.helidon.builder.processor.tools;

    provides io.helidon.builder.processor.spi.BuilderCreatorProvider
            with io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider;
    provides io.helidon.builder.processor.spi.TypeInfoCreatorProvider
            with io.helidon.builder.processor.tools.BuilderTypeTools;
}
