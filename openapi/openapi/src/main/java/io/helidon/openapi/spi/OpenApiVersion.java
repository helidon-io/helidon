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

import io.helidon.common.Api;
import io.helidon.common.media.type.MediaType;
import io.helidon.config.NamedService;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.service.registry.Service;

/**
 * OpenAPI version implementation.
 */
@Api.Preview
@Service.Contract
public interface OpenApiVersion extends NamedService {
    /**
     * Exact OpenAPI document version produced by this implementation.
     *
     * @return OpenAPI document version
     */
    String version();

    /**
     * Parse OpenAPI content into the version-neutral document model.
     *
     * @param context document context
     * @param content OpenAPI JSON or YAML content
     * @param mediaType media type guessed from the static document path
     * @return parsed document model
     */
    OpenApiDocument parse(OpenApiDocumentContext context, String content, MediaType mediaType);

    /**
     * Render an OpenAPI document.
     * <p>
     * OpenAPI 3.0 documents require a {@code paths} field. Use
     * {@link OpenApiDocument.Builder#paths(java.util.Map)} with {@code Map.of()} to render an intentionally empty
     * Paths Object. OpenAPI 3.1 and 3.2 documents require at least one of {@code paths}, {@code components}, or
     * {@code webhooks}.
     *
     * @param context document context
     * @param document version-neutral document model
     * @return rendered OpenAPI document content
     * @throws IllegalStateException if the document does not satisfy the root requirements for the rendered version
     */
    String render(OpenApiDocumentContext context, OpenApiDocument document);
}
