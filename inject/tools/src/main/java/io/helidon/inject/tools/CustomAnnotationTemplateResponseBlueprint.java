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

package io.helidon.inject.tools;

import java.util.Map;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * The response from {@link io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator#create(CustomAnnotationTemplateRequest)}.
 */
@Prototype.Blueprint
interface CustomAnnotationTemplateResponseBlueprint {

    /**
     * The request that was processed.
     *
     * @return the request that was processed
     */
    CustomAnnotationTemplateRequest request();

    /**
     * Any source that should be code generated.
     *
     * @return map of generated type name to body of the source to be generated
     */
    @Prototype.Singular
    Map<TypeName, String> generatedSourceCode();

    /**
     * Any generated resources should be generated.
     *
     * @return map of generated type name (which is really just a directory path under resources) to the resource to be generated
     */
    @Prototype.Singular
    Map<TypedElementInfo, String> generatedResources();
}
