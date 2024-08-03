/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
 * Codegen for Helidon Config Metadata.
 *
 * @see io.helidon.metadata.codegen.config;
 */
module io.helidon.metadata.codegen {
    requires io.helidon.codegen;
    requires io.helidon.metadata.hson;

    // this module is unusual, as it covers more features
    // (we do not need so detailed modularization for annotation processing)
    exports io.helidon.metadata.codegen.config;
    exports io.helidon.metadata.codegen.spotbugs;

    provides io.helidon.codegen.spi.CodegenExtensionProvider
            with io.helidon.metadata.codegen.config.ConfigMetadataCodegenProvider,
                    io.helidon.metadata.codegen.spotbugs.SpotbugsCodegenProvider;
}