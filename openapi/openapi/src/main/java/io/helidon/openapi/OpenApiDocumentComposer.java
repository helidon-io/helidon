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

package io.helidon.openapi;

import java.util.List;

import io.helidon.common.media.type.MediaType;
import io.helidon.openapi.spi.OpenApiDocumentSource;
import io.helidon.openapi.spi.OpenApiVersion;

final class OpenApiDocumentComposer {
    private OpenApiDocumentComposer() {
    }

    static String compose(OpenApiDocumentContext context,
                          OpenApiVersion staticOpenApiVersion,
                          String staticContent,
                          MediaType staticContentMediaType,
                          List<OpenApiDocumentSource> sources) {
        boolean hasStaticContent = !staticContent.isBlank();
        OpenApiGeneratedMode mode = context.generatedMode();
        if (mode == OpenApiGeneratedMode.STATIC_ONLY
                || (hasStaticContent && mode == OpenApiGeneratedMode.STATIC_FIRST)) {
            return staticContent;
        }
        if (sources.isEmpty()) {
            return mode == OpenApiGeneratedMode.GENERATED_ONLY ? "" : staticContent;
        }

        OpenApiDocument generated = generatedDocument(context, sources);
        if (generated.isEmpty()) {
            return mode == OpenApiGeneratedMode.GENERATED_ONLY ? "" : staticContent;
        }

        if (mode == OpenApiGeneratedMode.GENERATED_ONLY || !hasStaticContent) {
            return renderGenerated(context, generated);
        }

        if (mode == OpenApiGeneratedMode.MERGE) {
            OpenApiDocumentContext staticContext = new OpenApiDocumentContext(context.featureName(),
                                                                              context.webContext(),
                                                                              context.listener(),
                                                                              context.generatedMode(),
                                                                              staticOpenApiVersion);
            OpenApiDocument staticDocument = staticOpenApiVersion.parse(staticContext, staticContent, staticContentMediaType);
            OpenApiDocument merged = OpenApiDocument.builder()
                    .merge(staticDocument)
                    .merge(generated)
                    .build();
            return context.openApiVersion().render(context, merged);
        }

        return staticContent;
    }

    private static OpenApiDocument generatedDocument(OpenApiDocumentContext context, List<OpenApiDocumentSource> sources) {
        OpenApiDocument.Builder builder = OpenApiDocument.builder();
        for (OpenApiDocumentSource source : sources) {
            if (source.supports(context)) {
                source.describe(context, builder);
            }
        }
        return builder.build();
    }

    private static String renderGenerated(OpenApiDocumentContext context, OpenApiDocument generated) {
        return context.openApiVersion().render(context, generated);
    }
}
