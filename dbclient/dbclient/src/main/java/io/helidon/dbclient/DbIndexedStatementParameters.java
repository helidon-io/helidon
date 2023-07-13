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

import java.util.LinkedList;
import java.util.List;

/**
 * Indexed {@link DbStatementParameters}.
 */
public class DbIndexedStatementParameters extends DbStatementParameters {

    private final List<Object> parameters = new LinkedList<>();

    @Override
    public DbStatementParameters addParam(Object parameter) {
        parameters.add(parameter);
        return this;
    }

    /**
     * Return {@code List} containing all ordered parameters.
     *
     * @return {@code List} containing all ordered parameters
     */
    public List<Object> parameters() {
        return parameters;
    }
}
