/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
package io.helidon.codegen.classmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

abstract class AnnotatedComponent extends CommonComponent {

    private final List<Annotation> annotations;

    AnnotatedComponent(Builder<?, ?> builder) {
        super(builder);
        annotations = List.copyOf(builder.annotations);
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        super.addImports(imports);
        annotations.forEach(annotation -> annotation.addImports(imports));
    }

    /**
     * List of annotations on this component.
     *
     * @return annotations
     */
    public List<Annotation> annotations() {
        return List.copyOf(annotations);
    }

    abstract static class Builder<B extends Builder<B, T>, T extends AnnotatedComponent> extends CommonComponent.Builder<B, T> {

        private final List<Annotation> annotations = new ArrayList<>();

        Builder() {
        }

        @Override
        public B description(String description) {
            return super.description(description);
        }

        @Override
        public B description(List<String> description) {
            return super.description(description);
        }

        @Override
        public B addDescriptionLine(String line) {
            return super.addDescriptionLine(line);
        }

        /**
         * Add new annotation to the component.
         *
         * @param annotation annotation instance
         * @return updated builder instance
         */
        public B addAnnotation(io.helidon.common.types.Annotation annotation) {
            return addAnnotation(Annotation.create(annotation));
        }

        /**
         * Add new annotation to the component.
         *
         * @param consumer annotation builder consumer
         * @return updated builder instance
         */
        public B addAnnotation(Consumer<Annotation.Builder> consumer) {
            Annotation.Builder builder = Annotation.builder();
            consumer.accept(builder);
            return addAnnotation(builder.build());
        }

        /**
         * Add new annotation to the component.
         *
         * @param builder annotation builder
         * @return updated builder instance
         */
        public B addAnnotation(Annotation.Builder builder) {
            return addAnnotation(builder.build());
        }

        /**
         * Add new annotation to the component.
         *
         * @param annotation annotation instance
         * @return updated builder instance
         */
        public B addAnnotation(Annotation annotation) {
            annotations.add(annotation);
            return identity();
        }

        @Override
        public B name(String name) {
            return super.name(name);
        }

    }

}
