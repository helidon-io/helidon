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

package io.helidon.builder.test.testsubjects;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Blueprint with a supplier from configuration.
 */
@Prototype.Blueprint
@Configured
interface SupplierBeanBlueprint {
    /**
     * This value is either explicitly configured, or uses config to get the supplier.
     * If config source with change support is changed, the supplier should provide the latest value from configuration.
     *
     * @return supplier with latest value
     */
    @ConfiguredOption
    Supplier<String> stringSupplier();

    @ConfiguredOption(key = "string-supplier")
    Supplier<char[]> charSupplier();

    @ConfiguredOption
    Supplier<Optional<String>> optionalSupplier();

    @ConfiguredOption(key = "optional-supplier")
    Supplier<Optional<char[]>> optionalCharSupplier();
}
