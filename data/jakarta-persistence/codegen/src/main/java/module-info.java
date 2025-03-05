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
 * Code processing and generation with Jakarta Persistence API.
 */
module io.helidon.data.jakarta.persistence.codegen {

    requires io.helidon.codegen;

    requires io.helidon.data.codegen.common;

    exports io.helidon.data.jakarta.persistence.codegen;

    provides io.helidon.data.codegen.common.spi.PersistenceGeneratorProvider
            with io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceGeneratorProvider;

    provides io.helidon.codegen.spi.CodegenExtensionProvider
            with io.helidon.data.jakarta.persistence.codegen.EntityCodegenProvider;
}
