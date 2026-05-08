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

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;

/**
 * A base class that can be used to implement media support that has a few common features,
 * such as support for {@link MediaSupportConfig}.
 * <p>
 * This base class provides checks for all four methods (client request, client response, server request, server response)
 * and makes sure that the type is supported, and that media types match.
 *
 * @param <T> the configuration object used by the subtype, must extend {@link io.helidon.http.media.MediaSupportConfig}
 */
public abstract class MediaSupportBase<T extends MediaSupportConfig> implements MediaSupport {
    private final T config;
    private final String type;

    /**
     * Construct a new support base for the specified type.
     *
     * @param config a {@link io.helidon.http.media.MediaSupportConfig} subtype to configure this instance
     * @param type type of the media support, such as {@code json-binding}
     */
    protected MediaSupportBase(T config, String type) {
        this.config = config;
        this.type = type;

    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return type;
    }

    /**
     * Server request reader check.
     *
     * @param type           requested type
     * @param requestHeaders server request headers
     * @return whether we support this request and can provide a reader
     */
    protected boolean matchesServerRequest(GenericType<?> type, Headers requestHeaders) {
        if (!canDeserialize(type)) {
            // not a valid type (i.e. requested `MyObject` but we support `JsonValue`)
            return false;
        }
        return requestHeaders.contentType()
                .map(this::isMediaTypeSupported)
                .orElse(true);
    }

    /**
     * Server response writer check.
     * Checks our content type against the
     * {@code Accept} header of the request.
     *
     * @param type            provided type
     * @param requestHeaders  server request headers
     * @param responseHeaders server response headers
     * @return whether we support this response and can provide a writer
     */
    protected boolean matchesServerResponse(GenericType<?> type,
                                            Headers requestHeaders,
                                            Headers responseHeaders) {
        if (!canSerialize(type)) {
            // not a valid type (i.e. provided `MyObject` but we support `JsonValue`)
            return false;
        }

        HttpMediaType contentType = responseHeaders.contentType()
                .orElse(config.contentType());

        if (!isMediaTypeSupported(contentType)) {
            return false;
        }

        return requestHeaders.isAccepted(contentType);
    }

    /**
     * Client request writer check.
     * Checks if the content type and type is supported.
     *
     * @param type           provided type
     * @param requestHeaders client request headers
     * @return whether we support this request and can provide a writer
     */
    protected boolean matchesClientRequest(GenericType<?> type, Headers requestHeaders) {
        if (!canSerialize(type)) {
            // not a valid type (i.e. provided `MyObject` but we support `JsonValue`)
            return false;
        }

        HttpMediaType contentType = requestHeaders.contentType()
                .orElse(config.contentType());

        return isMediaTypeSupported(contentType);
    }

    /**
     * Client response reader check.
     * Checks if the content type and type is supported.
     *
     * @param type            requested type
     * @param responseHeaders client response headers
     * @return whether we support this response and can provide a reader
     */
    protected boolean matchesClientResponse(GenericType<?> type, Headers responseHeaders) {
        if (!canDeserialize(type)) {
            // not a valid type (i.e. provided `MyObject` but we support `JsonValue`)
            return false;
        }

        return responseHeaders.contentType()
                .map(this::isMediaTypeSupported)
                .orElse(true);
    }

    /**
     * Check whether the provided media type is one of the supported media types.
     *
     * @param mediaType media type to check
     * @return whether it is a supported media type
     */
    protected boolean isMediaTypeSupported(HttpMediaType mediaType) {
        for (MediaType acceptedMediaType : config.acceptedMediaTypes()) {
            if (mediaType.test(acceptedMediaType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The configuration object used to create this instance.
     *
     * @return the config object
     */
    protected T config() {
        return config;
    }

    /**
     * Check whether the type is supported by this media support for serialization.
     *
     * @param type type to check
     * @return whether this media support can handle this type
     */
    protected abstract boolean canSerialize(GenericType<?> type);

    /**
     * Check whether the type is supported by this media support for deserialization.
     *
     * @param type type to check
     * @return whether this media support can handle this type
     */
    protected abstract boolean canDeserialize(GenericType<?> type);
}
