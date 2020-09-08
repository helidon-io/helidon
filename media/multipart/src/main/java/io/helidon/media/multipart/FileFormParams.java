/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.nio.file.Path;

import io.helidon.common.http.FormBuilder;

/**
 * Form object which simplifies sending of multipart forms.
 */
public interface FileFormParams {

    /**
     * Create a new builder for {@link FileFormParams}.
     *
     * @return new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder of {@link FileFormParams}.
     */
    class Builder implements FormBuilder<Builder, FileFormParams> {

        private final WriteableMultiPart.Builder builder = WriteableMultiPart.builder();

        private Builder() {
        }

        @Override
        public FileFormParams build() {
            return new FileFormParamsImpl(builder.build().bodyParts());
        }

        @Override
        public Builder add(String name, String... values) {
            for (String value : values) {
                builder.bodyPart(name, value);
            }
            return this;
        }

        /**
         * Add file with specific name and filename to the form.
         *
         * @param name content disposition name
         * @param fileName content disposition filename
         * @param file file path
         * @return update builder instance
         */
        public Builder addFile(String name, String fileName, Path file) {
            builder.bodyPart(name, fileName, file);
            return this;
        }

        /**
         * Add files with specific name to the form.
         *
         * Filename parameter is based on an actual name of the file.
         *
         * @param name content disposition name
         * @param files files
         * @return update builder instance
         */
        public Builder addFile(String name, Path... files) {
            builder.bodyPart(name, files);
            return this;
        }

    }

}
