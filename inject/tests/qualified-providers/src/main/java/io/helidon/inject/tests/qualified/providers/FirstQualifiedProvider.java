/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.qualified.providers;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.QualifiedProvider;
import io.helidon.inject.service.Qualifier;

@Injection.Singleton
class FirstQualifiedProvider implements QualifiedProvider<FirstQualifier, Object> {
    private final Map<String, String> values = Map.of("first", "first",
                                                      "second", "49");

    @Override
    public Optional<QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        Optional<String> stringValue = Optional.of(values.get(qualifier.value().orElse("not-defined")));

        return stringValue.map(str -> QualifiedInstance.create(mapType(str, type), qualifier));
    }

    private Object mapType(String str, GenericType<Object> type) {
        if (type.equals(GenericType.OBJECT) || type.equals(GenericType.STRING)) {
            return str;
        }
        if (type.rawType().equals(Integer.class) || type.rawType().equals(int.class)) {
            return Integer.parseInt(str);
        }
        throw new IllegalArgumentException("This provider only supports string and int, but " + type.getTypeName() + " was "
                                                   + "requested");
    }
}
