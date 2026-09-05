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
 * Generated-source fixture whose requested-key count exceeds the convenient
 * collection-factory limit.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface WideGeneratedKeyRepository {

    /**
     * Requests eleven named key columns from one generated update.
     *
     * @return first generated key
     */
    @Jdbc.Statement("INSERT INTO WIDE_KEY DEFAULT VALUES")
    @Jdbc.GeneratedKeys({"KEY_01", "KEY_02", "KEY_03", "KEY_04", "KEY_05", "KEY_06",
                         "KEY_07", "KEY_08", "KEY_09", "KEY_10", "KEY_11"})
    long insert();
}
