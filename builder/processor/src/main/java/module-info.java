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
 * The Builder annotation processor module.
 */
module io.helidon.builder.processor {
    requires java.compiler;
    requires io.helidon.common;
    requires io.helidon.builder;
    requires io.helidon.builder.processor.spi;
    requires io.helidon.builder.processor.tools;
    requires io.helidon.builder.types;

    exports io.helidon.builder.processor;

    provides javax.annotation.processing.Processor
            with io.helidon.builder.processor.BuilderProcessor;

    uses io.helidon.builder.processor.spi.BuilderCreatorProvider;
    uses io.helidon.builder.processor.spi.TypeInfoCreatorProvider;
}
