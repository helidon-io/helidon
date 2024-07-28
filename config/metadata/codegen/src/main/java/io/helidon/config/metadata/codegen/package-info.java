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
 * <p>
 * The code generator triggers on both Helidon Builder configuration annotations, and on
 * Helidon Config Metadata configuration annotations.
 * <p>
 * This codegen generates a {@code META-INF/helidon/config-metadata.json} for configuration options in the module.
 */
package io.helidon.config.metadata.codegen;
