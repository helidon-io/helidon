/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.spi;

import io.helidon.codegen.RoundContext;

/**
 * Code processing and generation extension.
 */
public interface CodegenExtension {
    /**
     * Process a round of code analysis and generation.
     * There may be more than one round of processing (such as when a type gets generated that has supported annotations that
     * need to be processed).
     *
     * @param roundContext context of the current round, used to get types to process, and to provide types for code generation
     */
    void process(RoundContext roundContext);

    /**
     * Processing has finished, any finalization can be done.
     *
     * @param roundContext context with no available types for processing, still can add types to code generate
     */
    default void processingOver(RoundContext roundContext) {
    }
}
