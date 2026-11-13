/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.query;

import io.helidon.common.Api;

/**
 * Projection action, e.g. {@code SELECT}, {@code DELETE}, {@code UPDATE}.
 */
@Api.Internal
public enum ProjectionAction {
    /**
     * {@code SELECT} action.
     */
    Select,
    /**
     * {@code DELETE} action.
     */
    Delete,
    /**
     * {@code UPDATE} action.
     */
    Update;

    /**
     * Whether query is DML (data manipulation language) or not.
     *
     * @return value of {@code true} when query is DML or {@code false} otherwise.
     */
    public boolean isDml() {
        return switch (this) {
            case Select -> false;
            case Delete,
                 Update -> true;
        };
    }

}
