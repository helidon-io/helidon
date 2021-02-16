/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.blocking;

import io.helidon.common.GenericType;

/**
 * Access to request entity using blocking API.
 */
public interface BlockingContent {
    /**
     * Consumes and converts the request content into the requested type.
     * <p>
     * The conversion requires an appropriate reader to be registered with webserver
     * {@link io.helidon.webserver.WebServer.Builder#addMediaSupport(io.helidon.media.common.MediaSupport)} or
     * {@link io.helidon.webserver.WebServer.Builder#addReader(io.helidon.media.common.MessageBodyReader)}.
     *
     * @param <T>  the requested type
     * @param type the requested type class
     * @return entity as the expected type
     * @throws java.lang.RuntimeException when the entity cannot be read
     */
    <T> T as(Class<T> type);

    /**
     * Consumes and converts the request content into the requested type.
     * <p>
     * The conversion requires an appropriate reader to be registered with webserver
     * {@link io.helidon.webserver.WebServer.Builder#addMediaSupport(io.helidon.media.common.MediaSupport)} or
     * {@link io.helidon.webserver.WebServer.Builder#addReader(io.helidon.media.common.MessageBodyReader)}.
     *
     * @param <T>  the requested type
     * @param type the requested type, can be a generic type
     * @return entity as the expected type
     * @throws java.lang.RuntimeException when the entity cannot be read
     */
    <T> T as(GenericType<T> type);
}
