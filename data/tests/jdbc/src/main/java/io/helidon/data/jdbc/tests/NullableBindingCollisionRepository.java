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
package io.helidon.data.jdbc.tests;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Generated-source fixture that reserves the preferred nullable-bind helper
 * name on its repository contract.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface NullableBindingCollisionRepository {

    /**
     * Binds two nullable values so codegen must emit its shared helper.
     *
     * @param name replacement name
     * @param label replacement label
     */
    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name, LABEL = :label WHERE PREVIOUS_NAME = :name")
    void update(String name, String label);

    /**
     * Reserves the preferred generated helper name through an inherited
     * instance method.
     */
    default void bindParameter() {
    }
}
