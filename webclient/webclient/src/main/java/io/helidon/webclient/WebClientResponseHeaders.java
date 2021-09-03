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
package io.helidon.webclient;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.SetCookie;

/**
 * Headers that may be available on response from server.
 */
public interface WebClientResponseHeaders extends Headers {

    /**
     * Returns {@link SetCookie} header of the response.
     *
     * @return set cookie header
     */
    List<SetCookie> setCookies();

    /**
     * Returns {@link URI} representation of {@value Http.Header#LOCATION} header from the response.
     *
     * @return location header uri
     */
    Optional<URI> location();

    /**
     * Returns value of header {@value Http.Header#LAST_MODIFIED} of the response.
     *
     * @return LAST_MODIFIED header value.
     */
    Optional<ZonedDateTime> lastModified();

    /**
     * Returns value of header {@value Http.Header#EXPIRES} of the response.
     *
     * @return EXPIRES header value.
     */
    Optional<ZonedDateTime> expires();

    /**
     * Returns value of header {@value Http.Header#DATE} of the response.
     *
     * @return DATE header value.
     */
    Optional<ZonedDateTime> date();

    /**
     * Returns content type of the response.
     *
     * @return content type of the response
     */
    Optional<MediaType> contentType();

    /**
     * Returns value of header {@value Http.Header#ETAG} of the response.
     *
     * @return ETAG header value.
     */
    Optional<String> etag();

    /**
     * Content length of the response payload.
     *
     * @return content length
     */
    Optional<Long> contentLength();

    /**
     * Transfer encoding of the response.
     *
     * @return transfer encoding
     */
    List<String> transferEncoding();


}
