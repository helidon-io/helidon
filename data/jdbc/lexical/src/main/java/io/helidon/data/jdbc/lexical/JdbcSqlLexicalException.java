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
package io.helidon.data.jdbc.lexical;

/**
 * Reports malformed SQL lexical input without retaining the SQL text.
 */
final class JdbcSqlLexicalException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a failure at one source offset.
     *
     * @param problem natural language description of the problem
     * @param profile lexical profile used for the scan
     * @param offset source offset where recognition failed
     */
    JdbcSqlLexicalException(String problem, JdbcSqlLexicalProfile profile, int offset) {
        super(problem + ". The lexical profile is " + profile + ", and the SQL offset is " + offset + ".");
    }
}
