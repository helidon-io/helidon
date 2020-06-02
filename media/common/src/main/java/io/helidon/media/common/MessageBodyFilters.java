/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.media.common;

import java.util.concurrent.Flow;

/**
 * Registry of {@link MessageBodyFilters}.
 */
public interface MessageBodyFilters {

    /**
     * Registers a message body filter.
     * <p>
     * The registered filters are applied to form a chain from the first registered to the last registered.
     * The first evaluation of the function transforms the original publisher to a new publisher. Any subsequent
     * evaluation receives the publisher transformed by the last previously registered filter.
     *
     * @param filter a function to map previously registered or original {@code Publisher} to the new one. If returns
     *               {@code null} then the result will be ignored.
     * @return this instance of {@link MessageBodyFilters}
     * @see MessageBodyContext#applyFilters(Flow.Publisher)
     * @throws NullPointerException if parameter {@code function} is {@code null}
     */
    MessageBodyFilters registerFilter(MessageBodyFilter filter);
}
