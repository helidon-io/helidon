/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.parameters;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

class ParametersSingleValueMap implements Parameters {
    private final String component;
    private final Map<String, String> params;

    ParametersSingleValueMap(String component, Map<String, String> params) {
        this.component = component;
        this.params = params;
    }

    @Override
    public List<String> all(String name) throws NoSuchElementException {
        String value = params.get(name);
        if (value == null) {
            throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
        }
        return List.of(value);
    }

    @Override
    public String value(String name) throws NoSuchElementException {
        String value = params.get(name);
        if (value == null) {
            throw new NoSuchElementException("This " + component + " does not contain parameter named \"" + name + "\"");
        }
        return value;
    }

    @Override
    public boolean contains(String name) {
        return params.containsKey(name);
    }

    @Override
    public int size() {
        return params.size();
    }

    @Override
    public Set<String> names() {
        return Set.copyOf(params.keySet());
    }

    @Override
    public String component() {
        return component;
    }

    @Override
    public String toString() {
        return component + ": " + params;
    }
}
