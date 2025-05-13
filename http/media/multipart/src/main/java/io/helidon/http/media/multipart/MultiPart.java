/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.Iterator;

import io.helidon.common.GenericType;

/**
 * Multi part message is an iterator of parts. The iterator can be traversed one time only,
 * as the data is read during processing (so we do not create parts in memory).
 * This type can be used with {@code ReadableEntity#as(Class)}.
 */
public abstract class MultiPart implements Iterator<ReadablePart> {
    /**
     * Generic type of {@link MultiPart}.
     */
    public static final GenericType<MultiPart> GENERIC_TYPE = GenericType.create(MultiPart.class);

    /**
     * This class is empty, this constructor does nothing.
     */
    public MultiPart() {
    }
}
