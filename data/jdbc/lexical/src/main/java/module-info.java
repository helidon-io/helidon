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

/**
 * Shared lexical support for JDBC SQL marker processing.
 *
 * <p>JDBC code generation uses this module to recognize named and positional
 * markers while producing validated positional SQL. The JDBC runtime uses it
 * to validate imperative SQL and count positional markers before creating a
 * statement. The module recognizes lexical regions and reports marker events,
 * while each consumer remains responsible for rewriting or counting the
 * reported markers.</p>
 */
module io.helidon.data.jdbc.lexical {

    requires static io.helidon.common;

    exports io.helidon.data.jdbc.lexical to
            io.helidon.data.jdbc,
            io.helidon.data.jdbc.codegen;
}
