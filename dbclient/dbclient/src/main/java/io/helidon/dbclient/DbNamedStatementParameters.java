/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.util.HashMap;
import java.util.Map;

/**
 * Named {@link DbStatementParameters}.
 */
public class DbNamedStatementParameters extends DbStatementParameters {

    private final Map<String, Object> parameters = new HashMap<>();

    @Override
    public DbStatementParameters addParam(String name, Object parameter) {
        parameters.put(name, parameter);
        return this;
    }

    /**
     * Return {@code Map} containing all named parameters.
     *
     * @return {@code Map} containing all named parameters
     */
    public Map<String, Object> parameters() {
        return parameters;
    }
}
