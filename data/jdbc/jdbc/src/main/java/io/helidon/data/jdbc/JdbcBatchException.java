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
package io.helidon.data.jdbc;

import io.helidon.common.Api;
import io.helidon.data.DataException;

/**
 * Reports a sanitized batch failure with detached evidence for every captured item.
 * Counts do not establish transaction completion. Only the provider constructs this exception.
 */
@Api.Incubating
public final class JdbcBatchException extends DataException {
    private final JdbcClient.BatchResult result;

    JdbcBatchException(JdbcClient.BatchResult result, Throwable cause) {
        super("The JDBC batch failed.", cause);
        this.result = result;
    }

    /**
     * Returns the evidence available before failure, including successful execution followed by cleanup failure.
     *
     * @return The immutable result contains one entry per captured item.
     */
    public JdbcClient.BatchResult result() {
        return result;
    }
}
