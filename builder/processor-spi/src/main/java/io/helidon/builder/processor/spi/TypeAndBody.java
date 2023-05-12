/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor.spi;

import io.helidon.common.types.TypeName;

/**
 * Represents the generated source as a model object.
 */
public interface TypeAndBody {

    /**
     * The type name for the source to be generated.
     *
     * @return The type name for the source to be generated
     */
    TypeName typeName();

    /**
     * The body for the source to be generated.
     *
     * @return The body for the source to be generated
     */
    String body();

}
