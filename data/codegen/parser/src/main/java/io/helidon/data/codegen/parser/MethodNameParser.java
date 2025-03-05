/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.parser;

import java.util.List;

import io.helidon.data.codegen.query.DataQuery;

/**
 * Method name parser interface.
 */
public interface MethodNameParser {

    /**
     * Create {@link MethodNameParser} from available {{@link io.helidon.data.codegen.parser.spi.MethodNameParserProvider}}s.
     *
     * @return new {@link MethodNameParser} instance
     */
    static MethodNameParser create() {
        return MethodNameParserProviders.list()
                .getFirst()
                .create();
    }

    /**
     * Parse method name.
     *
     * @param methodName method name to be parsed
     * @return value of {@code true} when method name matches grammar rules or {@code false} otherwise
     */
    boolean parse(String methodName);

    /**
     * Pass parser token events to provided tokens events listener.
     *
     * @return abstract data query
     */
    DataQuery dataQuery();

    /**
     * {@link List} of method name parsing errors.
     *
     * @return {@link List} of errors
     */
    List<MethodParserError> errors();

}
