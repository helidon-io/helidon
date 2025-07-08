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

/**
 * Models used by declarative codegen.
 * <p>
 * This module is to separate code generation of builders that are used by the declarative codegen.
 */
module io.helidon.declarative.codegen.model {
    requires io.helidon.builder.api;
    requires io.helidon.common.types;

    exports io.helidon.declarative.codegen.model.http to io.helidon.declarative.codegen;
}