/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media.multipart;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.GenericType;

/**
 * Multi part message is an iterator of parts. The iterator can be traversed one time only,
 * as the data is read during processing (so we do not create parts in memory).
 * This type can be used when sending entities with server response and client request.
 */
public interface WriteableMultiPart extends Iterator<WriteablePart> {
    /**
     * Generic type for writable multi part.
     */
    GenericType<WriteableMultiPart> GENERIC_TYPE = GenericType.create(WriteableMultiPart.class);

    /**
     * Builder to construct a new multi part message.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link WriteableMultiPart}.
     */
    class Builder implements io.helidon.common.Builder<Builder, WriteableMultiPart> {
        private final List<WriteablePart> parts = new ArrayList<>();

        private Builder() {
        }

        @Override
        public WriteableMultiPart build() {
            return new WriteableMultiPartImpl(this);
        }

        /**
         * Add a new part.
         *
         * @param part part to add
         * @return updated builder
         */
        public Builder addPart(Supplier<? extends WriteablePart> part) {
            parts.add(part.get());
            return this;
        }

        /**
         * Add a new part.
         *
         * @param part part to add
         * @return updated builder
         */
        public Builder addPart(WriteablePart part) {
            parts.add(part);
            return this;
        }

        List<WriteablePart> parts() {
            return parts;
        }
    }
}
