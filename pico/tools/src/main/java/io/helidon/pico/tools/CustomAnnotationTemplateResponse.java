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

package io.helidon.pico.tools;

import java.util.Map;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.builder.types.TypeName;
import io.helidon.builder.types.TypedElementName;

/**
 * The response from {@link CustomAnnotationTemplateCreator#create(CustomAnnotationTemplateRequest)}.
 */
@Builder
public interface CustomAnnotationTemplateResponse {

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
    @Singular
    Map<TypeName, String> generatedSourceCode();

    /**
     * Any generated resources should be generated.
     *
     * @return map of generated type name (which is really just a directory path under resources) to the resource to be generated
     */
    @Singular
    Map<TypedElementName, String> generatedResources();

    /**
     * Aggregates the responses given to one response.
     *
     * @param request   the request being processed
     * @param responses the responses to aggregate into one response instance
     * @return the aggregated response
     */
    static DefaultCustomAnnotationTemplateResponse.Builder aggregate(
            CustomAnnotationTemplateRequest request,
            CustomAnnotationTemplateResponse... responses) {
        DefaultCustomAnnotationTemplateResponse.Builder response = DefaultCustomAnnotationTemplateResponse.builder()
                .request(request);
        for (CustomAnnotationTemplateResponse r : responses) {
            response.addGeneratedSourceCode(r.generatedSourceCode());
            response.addGeneratedResources(r.generatedResources());
        }
        return response;
    }

}
