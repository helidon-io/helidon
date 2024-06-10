/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tools.spi;

import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.tools.CustomAnnotationTemplateRequest;
import io.helidon.inject.tools.CustomAnnotationTemplateResponse;

/**
 * Instances of this are found via the service loader during compilation time and called by the
 * {@code io.helidon.inject.processor.CustomAnnotationProcessor}. It should be noted that this contract may be
 * called multiple times since annotation processing naturally happens over multiple iterations.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface CustomAnnotationTemplateCreator {

    /**
     * These are the set of annotation types that will trigger a call this producer.
     *
     * @return the supported annotation types for this producer
     */
    Set<TypeName> annoTypes();

    /**
     * The implementor should return empty if the request should not be handled.
     *
     * @param request the request
     * @return the response that will describe what template to produce, or empty to to cause processing to skip
     */
    Optional<CustomAnnotationTemplateResponse> create(CustomAnnotationTemplateRequest request);

}
