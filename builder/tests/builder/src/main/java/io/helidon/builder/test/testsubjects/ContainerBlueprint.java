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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Base for {@link PickleBarrel}.
 */
@Prototype.Blueprint
interface ContainerBlueprint {
    /**
     * The ID of the container (required to be non-null).
     *
     * @return the ID of this container
     */
    @Option.Required
    String id();

    /**
     * The type of container (not required).
     *
     * @return type of container if specified
     */
    @Option.Default("PLASTIC")
    Optional<ContainerType> type();

}
