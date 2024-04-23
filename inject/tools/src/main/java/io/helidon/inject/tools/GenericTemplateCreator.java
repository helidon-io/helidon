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

package io.helidon.inject.tools;

import java.util.Optional;

import io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator;

/**
 * Tools to assist with using {@link CustomAnnotationTemplateCreator}'s.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface GenericTemplateCreator {

    /**
     * Convenience method to help with the typical/generic case where the request + the provided generatedType
     * is injected into the supplied template to produce the response.
     *
     * @param request the generic template creator request
     *
     * @return the response, or empty if the template can not be generated for this case
     */
    Optional<CustomAnnotationTemplateResponse> create(GenericTemplateCreatorRequest request);

}
