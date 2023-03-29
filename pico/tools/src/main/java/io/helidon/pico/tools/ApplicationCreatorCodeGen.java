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

package io.helidon.pico.tools;

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Codegen request options applicable for {@link ApplicationCreator}.
 *
 * @see ApplicationCreator
 */
@Builder
public interface ApplicationCreatorCodeGen {

    /**
     * See {@link #classPrefixName()}.
     */
    String TEST_SCOPE = "test";

    /**
     * The package name to use for code generation.
     *
     * @return package name
     */
    Optional<String> packageName();

    /**
     * The class name to use for code generation.
     *
     * @return class name
     */
    Optional<String> className();

    /**
     * Typically populated as "test" if test scoped, otherwise left blank.
     *
     * @return production or test scope
     */
    @ConfiguredOption("")
    String classPrefixName();

}
