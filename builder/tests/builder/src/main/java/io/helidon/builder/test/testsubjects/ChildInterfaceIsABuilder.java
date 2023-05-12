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

package io.helidon.builder.test.testsubjects;

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Demonstrates builder usages when the parent in a plain old interface (and not a target of the builder annotation), while this
 * child interface is a target for the builder. The net result is that the builder generated will handle both the parent and the
 * child merged as one.
 */
@Builder(implPrefix = "", implSuffix = "Impl", allowPublicOptionals = true)
public interface ChildInterfaceIsABuilder extends ParentInterfaceNotABuilder {

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    long childLevel();

    /**
     * Used for testing {@link io.helidon.config.metadata.ConfiguredOption} default values.
     *
     * @return ignored, here for testing purposes only
     */
    @ConfiguredOption("true")
    boolean isChildLevel();

    /**
     * Used for testing {@link io.helidon.config.metadata.ConfiguredOption} default values.
     *
     * @return ignored, here for testing purposes only
     */
    @Override
    @ConfiguredOption("override")
    Optional<char[]> maybeOverrideMe();

    /**
     * Used for testing {@link io.helidon.config.metadata.ConfiguredOption} default values.
     *
     * @return ignored, here for testing purposes only
     */
    @Override
    @ConfiguredOption("override2")
    char[] overrideMe();

}
