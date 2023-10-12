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
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Shows usage of allowed values.
 */
@Prototype.Blueprint
@Prototype.Configured
interface AllowedValuesBlueprint {
    @Option.AllowedValue(value = "GOOD_1", description = "First good value")
    @Option.AllowedValue(value = "GOOD_2", description = "Second good value")
    @Option.Configured
    Optional<String> restrictedOptions();

    @Option.AllowedValue(value = "GOOD_1", description = "First good value")
    @Option.AllowedValue(value = "GOOD_2", description = "Second good value")
    @Option.Singular("restrictedOptionToList")
    @Option.Configured
    List<String> restrictedOptionsList();
}
