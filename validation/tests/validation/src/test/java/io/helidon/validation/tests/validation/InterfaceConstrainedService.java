/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.validation.Validation;

interface InterfaceConstrainedService {
    String validate(@Validation.String.NotBlank String value);

    default String validateDefault(@Validation.String.NotBlank String value) {
        return value;
    }

    List<String> validateList(List<@Validation.String.NotBlank String> values);

    List<List<String>> validateNestedList(List<List<@Validation.String.NotBlank String>> values);

    Map<String, String> validateMap(Map<@Validation.String.NotBlank String, @Validation.String.NotBlank String> values);

    Map<List<String>, String> validateNestedMapKey(Map<List<@Validation.String.NotBlank String>, String> values);

    Map<String, List<String>> validateNestedMapValue(Map<String, List<@Validation.String.NotBlank String>> values);

    Optional<String> validateOptional(Optional<@Validation.String.NotBlank String> value);

    @TypeUseNotBlank String[] validateArray(@TypeUseNotBlank String[] values);

    List<Integer> validateIntegerList(List<@Validation.Integer.Min(10) Integer> values);

    List<@Validation.String.NotBlank String> invalidNames();

    int validateMinimum(@Validation.Integer.Min(10) int value);

    String validateShared(@Validation.String.NotBlank String value);

    String validateDuplicate(@Validation.String.NotBlank String value);

    List<String> validateCustomStringList(@CustomConstraint List<String> values);

    List<Integer> validateCustomIntegerList(@CustomConstraint List<Integer> values);
}
