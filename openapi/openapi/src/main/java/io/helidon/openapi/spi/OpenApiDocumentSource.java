/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.openapi.spi;

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.service.registry.Service;

/**
 * Source of generated OpenAPI document metadata.
 * <p>
 * A source can be qualified with {@link Service.Named @Service.Named} so the OpenAPI feature can select it with
 * {@code generated.document-sources}. Unqualified sources always contribute when they {@link #supports(OpenApiDocumentContext)
 * support} the document context and cannot be filtered by {@code generated.document-sources}.
 */
@Api.Preview
@Service.Contract
public interface OpenApiDocumentSource {
    /**
     * Whether this source contributes to the provided document context.
     *
     * @param context document context
     * @return {@code true} if this source should contribute
     */
    default boolean supports(OpenApiDocumentContext context) {
        Objects.requireNonNull(context);
        return true;
    }

    /**
     * Describe this source as OpenAPI document metadata.
     *
     * @param context document context
     * @param document document builder
     */
    void describe(OpenApiDocumentContext context, OpenApiDocument.Builder document);
}
