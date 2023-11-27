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

package io.helidon.codegen;

import java.util.ServiceLoader;

import io.helidon.codegen.spi.GeneratedAnnotationProvider;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/**
 * Support for generated annotation.
 */
final class GeneratedAnnotationHandler {
    private static final GeneratedAnnotationProvider PROVIDER = HelidonServiceLoader.builder(ServiceLoader.load(
                    GeneratedAnnotationProvider.class))
            .addService(new DefaultProvider(), 0)
            .build()
            .iterator()
            .next();

    private GeneratedAnnotationHandler() {
    }

    /**
     * Create a generated annotation.
     *
     * @param generator     type of the generator (annotation processor)
     * @param trigger       type of the class that caused this type to be generated
     * @param generatedType type that is going to be generated
     * @param versionId     version of the generator
     * @param comments      additional comments, never use null (use empty string so they do not appear in annotation)
     * @return a new annotation to add to the generated type
     */
    static Annotation create(TypeName generator,
                             TypeName trigger,
                             TypeName generatedType,
                             String versionId,
                             String comments) {
        return PROVIDER.create(generator, trigger, generatedType, versionId, comments);
    }

    // @Generated(value = "io.helidon.inject.tools.ActivatorCreatorDefault", comments = "version=1")
    private static class DefaultProvider implements GeneratedAnnotationProvider {
        private static final TypeName GENERATED = TypeName.create("io.helidon.common.Generated");

        @Override
        public Annotation create(TypeName generator,
                                 TypeName trigger,
                                 TypeName generatedType,
                                 String versionId,
                                 String comments) {
            return Annotation.builder()
                    .typeName(GENERATED)
                    .putValue("value", generator.resolvedName())
                    .putValue("trigger", trigger.resolvedName())
                    .update(it -> {
                        if (!"1".equals(versionId)) {
                            it.putValue("version", versionId);
                        }
                    })
                    .update(it -> {
                        if (!comments.isBlank()) {
                            it.putValue("comments", comments);
                        }
                    })
                    .build();
        }
    }
}
