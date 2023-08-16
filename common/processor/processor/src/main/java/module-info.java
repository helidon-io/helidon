/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * Utilities for annotation processors.
 */
module io.helidon.common.processor {
    requires transitive java.compiler;
    requires transitive io.helidon.common.types;
    requires jdk.compiler;
    requires io.helidon.common.processor.classmodel;

    exports io.helidon.common.processor;
    exports io.helidon.common.processor.spi;

    uses io.helidon.common.processor.spi.CopyrightProvider;
    uses io.helidon.common.processor.spi.GeneratedAnnotationProvider;
}