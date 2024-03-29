/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

/**
 * POJO used to define {@code Pokemon} IDs range in query statement tests.
 *
 * @param idMin Beginning of IDs range.
 * @param idMax End of IDs range.
 */
public record RangePoJo(int idMin, int idMax) {

    public static final class Mapper implements DbMapper<RangePoJo> {

        public static final Mapper INSTANCE = new Mapper();

        @Override
        public RangePoJo read(DbRow row) {
            throw new UnsupportedOperationException("Read operation is not implemented.");
        }

        @Override
        public Map<String, ?> toNamedParameters(RangePoJo value) {
            Map<String, Object> params = new HashMap<>(2);
            params.put("idmin", value.idMin());
            params.put("idmax", value.idMax());
            return params;
        }

        @Override
        public List<?> toIndexedParameters(RangePoJo value) {
            List<Object> params = new ArrayList<>(2);
            params.add(value.idMin());
            params.add(value.idMax());
            return params;
        }

    }

    /**
     * Creates an instance of Range  POJO.
     *
     * @param idMin beginning of IDs range
     * @param idMax end of IDs range
     */
    public RangePoJo {
    }

    /**
     * Get beginning of IDs range.
     *
     * @return beginning of IDs range
     */
    @Override
    public int idMin() {
        return idMin;
    }

    /**
     * Get end of IDs range.
     *
     * @return end of IDs range
     */
    @Override
    public int idMax() {
        return idMax;
    }

}
