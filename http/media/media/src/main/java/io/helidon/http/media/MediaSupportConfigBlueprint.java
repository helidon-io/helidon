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

package io.helidon.http.media;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.HttpMediaType;

/**
 * A set of configurable options expected to be used by each media support.
 * It is optional to extend this type in the configuration blueprint of each support.
 * <p>
 * Media types are used to check if a media support should be used for a specific request/response,
 * and to configure the {@value io.helidon.http.HeaderNames#CONTENT_TYPE_NAME} header.
 * <h2>WebServer</h2>
 * <ul>
 *     <li>Reading of request entity - the content type of the request is usually checked against
 *          {@link #acceptedMediaTypes()}, and the type requested, and if both match, that support is used</li>
 *     <li>Writing of response entity - the {@link #contentType()} is used as a value for the
 *          {@value io.helidon.http.HeaderNames#CONTENT_TYPE_NAME} header, and also validated against
 *          request {@value io.helidon.http.HeaderNames#ACCEPT_NAME} header values</li>
 * </ul>
 * <h2>WebClient</h2>
 * <ul>
 *     <li>Writing of request entity - the {@link #contentType()} is used as a value for the
 *           {@value io.helidon.http.HeaderNames#CONTENT_TYPE_NAME} header</li>
 *      <li>Reading of response entity - the content type is validated against {@link #acceptedMediaTypes()},
 *            and the type requested, and if both match, that support is used</li>
 * </ul>
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.CustomMethods(MediaConfigSupport.CustomMethods.class)
interface MediaSupportConfigBlueprint {
    /**
     * Name of the support. Each extension should provide its own default.
     * This is to enable multiple instance of the same type.
     *
     * @return name of the support
     */
    @Option.Configured
    String name();

    /**
     * Types accepted by this media support.
     * When server processes the response, it checks the {@code Accept} header, to choose the right
     * media support, if there are more supports available for the provided entity object.
     * <p>
     * NOTE Make sure that you accept the type returned by {@link #contentType()}.
     *
     * @return accepted media types
     */
    @Option.Singular
    @Option.Configured
    Set<MediaType> acceptedMediaTypes();

    /**
     * Content type to use if not configured (in response headers for server, and in request headers for client).
     *
     * @return content type to use
     */
    @Option.Configured
    HttpMediaType contentType();
}
