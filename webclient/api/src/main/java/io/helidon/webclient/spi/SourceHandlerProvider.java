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

package io.helidon.webclient.spi;

import io.helidon.common.GenericType;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.HttpClientResponse;

/**
 * {@link java.util.ServiceLoader} provider interface for {@link Source} handlers.
 *
 * @param <T> event type
 */
public interface SourceHandlerProvider<T> {

    /**
     * Checks if a provider supports the type.
     *
     * @param type the source type
     * @param response the HTTP response
     * @return outcome of test
     */
    boolean supports(GenericType<? extends Source<?>> type, HttpClientResponse response);

    /**
     * Handles a source.
     *
     * @param source the source
     * @param response the HTTP response
     * @param mediaContext the media context
     * @param <X> type of source
     */
    <X extends Source<T>> void handle(X source, HttpClientResponse response, MediaContext mediaContext);
}
