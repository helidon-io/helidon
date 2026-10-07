/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
 * The built-in multipart reader transfers ownership of its input stream to the returned message and closes
 * it when iteration completes. Use try-with-resources when processing can stop before the last part.
 * Closing the message closes its retained input stream without iterating the remaining parts itself.
 * The stream's close behavior still applies: closing a nested multipart message drains the enclosing part
 * through its boundary, which can read additional input, and leaves the outer message available for further iteration.
 * Transport-specific request or response cleanup still applies. Closing an individual part's input stream
 * likewise finishes only that part and leaves its message available for further iteration.
 */
public abstract class MultiPart implements Iterator<ReadablePart>, AutoCloseable {
    /**
     * Generic type of {@link MultiPart}.
     */
    public static final GenericType<MultiPart> GENERIC_TYPE = GenericType.create(MultiPart.class);

    /**
     * This class is empty, this constructor does nothing.
     */
    public MultiPart() {
    }

    /**
     * Release resources owned by this multipart message.
     * The built-in reader's message closes its retained input stream without iterating the remaining parts itself.
     * Closing a nested multipart message drains its enclosing part through the part boundary;
     * this can read additional input and leaves the outer message available for further iteration.
     * Afterwards {@link #hasNext()} returns {@code false} and {@link #next()} throws
     * {@link java.util.NoSuchElementException}. Repeated calls have no effect, including after a close failure.
     *
     * The default implementation does nothing to preserve compatibility with existing subclasses.
     * Subclasses that own resources must override this method to release them and stop iteration.
     *
     * @throws java.io.UncheckedIOException if closing the built-in reader's input stream fails
     */
    @Override
    public void close() {
    }
}
