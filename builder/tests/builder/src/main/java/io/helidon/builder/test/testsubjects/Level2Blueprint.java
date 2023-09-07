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

import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Demonstrates multi-level inheritance for the generated builder.
 */
@Prototype.Blueprint(beanStyle = true)
interface Level2Blueprint extends Level1Blueprint {

    /**
     * Used for testing and demonstrating option settings.
     *
     * @return ignored, here for testing purposes only
     */
    @Override
    @Option.Default("2")
    String getLevel0StringAttribute();

    /**
     * Used for testing and demonstrating the use of {@link io.helidon.builder.api.Option.Singular}.
     *
     * @return ignored, here for testing purposes only
     */
    @Option.Singular
    List<Level0> getLevel2Level0Info();

    /**
     * Used for testing and demonstrating the use of {@link io.helidon.builder.api.Option.Singular}.
     *
     * @return ignored, here for testing purposes only
     */
    @Option.Singular("Level0")
    List<Level0> getLevel2ListOfLevel0s();

    /**
     * Used for testing and demonstrating the use of {@link io.helidon.builder.api.Option.Singular}.
     *
     * @return ignored, here for testing purposes only
     */
    @Option.Singular("stringToLevel1")
    Map<String, Level1> getLevel2MapOfStringToLevel1s();

}
