/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.validation.tests.validation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.validation.Validation;

@Validation.Validated
class NestedValidated {
    @Validation.NotNull final String value;
    final Optional<@Validation.Valid ValidatedType> validatedType;
    final Set<@Validation.String.NotBlank @Validation.String.Length(4) String> validatedSet = new HashSet<>();
    final Map<@Validation.String.NotBlank @Validation.String.Length(4) String,
            @Validation.String.NotBlank @Validation.String.Length(7) String> validatedMap = new HashMap<>();
    final String notValidated = "Hello World!";

    private final long bigNumber;

    NestedValidated(String value, long bigNumber, ValidatedType validatedType) {
        this.value = value;
        this.bigNumber = bigNumber;
        this.validatedType = Optional.ofNullable(validatedType);
    }

    @Validation.Long.Max(14569L)
    public long bigNumber() {
        return bigNumber;
    }

    @Validation.Long.Min(0L)
    public long getOtherNumber() {
        return bigNumber;
    }
}
