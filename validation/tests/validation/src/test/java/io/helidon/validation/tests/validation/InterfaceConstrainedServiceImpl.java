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

import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;

@Service.Singleton
class InterfaceConstrainedServiceImpl
        implements UnconstrainedInterfaceConstrainedService,
                   InterfaceConstrainedService,
                   LowerMinimumInterfaceConstrainedService,
                   GenericInterfaceConstrainedService<String>,
                   OrderedGenericInterfaceConstrainedService<String, Integer>,
                   ChildGenericInterfaceConstrainedService<String> {
    @Override
    public String validate(String value) {
        return value;
    }

    @Override
    public List<String> validateList(List<String> values) {
        return values;
    }

    @Override
    public List<List<String>> validateNestedList(List<List<String>> values) {
        return values;
    }

    @Override
    public Map<String, String> validateMap(Map<String, String> values) {
        return values;
    }

    @Override
    public Map<List<String>, String> validateNestedMapKey(Map<List<String>, String> values) {
        return values;
    }

    @Override
    public Map<String, List<String>> validateNestedMapValue(Map<String, List<String>> values) {
        return values;
    }

    @Override
    public Optional<String> validateOptional(Optional<String> value) {
        return value;
    }

    @Override
    public String[] validateArray(String[] values) {
        return values;
    }

    @Override
    public List<Integer> validateIntegerList(List<Integer> values) {
        return values;
    }

    @Override
    public List<String> invalidNames() {
        return List.of("");
    }

    @Override
    public int validateMinimum(@Validation.Integer.Min(1) int value) {
        return value;
    }

    @Override
    public String validateShared(String value) {
        return value;
    }

    @Override
    public String validateDuplicate(@Validation.String.NotBlank String value) {
        return value;
    }

    @Override
    public List<String> validateCustomStringList(List<String> values) {
        return values;
    }

    @Override
    public List<Integer> validateCustomIntegerList(List<Integer> values) {
        return values;
    }

    @Override
    public String validateGeneric(String value) {
        return value;
    }

    @Override
    public Integer validateOrderedGeneric(String key, Integer value) {
        return value;
    }

    @Override
    public String validateInheritedGeneric(String value) {
        return value;
    }
}
