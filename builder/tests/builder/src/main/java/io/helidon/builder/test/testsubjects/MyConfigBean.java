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

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.metadata.ConfiguredValue;

/**
 * Used for demonstrating and testing the Builder.
 *
 * @see MyDerivedConfigBean
 */
@Builder(requireLibraryDependencies = false, requireBeanStyle = true, implPrefix = "", implSuffix = "Impl")
public interface MyConfigBean {

    /**
     * Used for demonstrating and testing the Builder. Here we can see that a {@code required=true} is placed on the configured
     * option.
     *
     * @return ignored, here for testing purposes only
     */
    @ConfiguredOption(required = true)
    String getName();

    /**
     * Used for testing and demonstrating usage.
     *
     * @return ignored, here for testing purposes only
     */
    boolean isEnabled();

    /**
     * Used for testing and demonstrating usage of {@link io.helidon.config.metadata.ConfiguredOption}
     * and {@link io.helidon.config.metadata.ConfiguredValue}.
     *
     * @return ignored, here for testing purposes only
     */
    @ConfiguredOption(key = "port", value = "8080", allowedValues = {
            @ConfiguredValue(value = "8080", description = "t1"),
            @ConfiguredValue(value = "false", description = "t2")})
    int getPort();

}
