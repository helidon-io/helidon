/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import io.helidon.common.parameters.Parameters;

/**
 * Segment of a path.
 */
public interface UriPathSegment {
    /**
     * Create a new path segment from raw (encoded) segment value that may contain matrix parameters.
     *
     * @param pathSegment raw path segment
     * @return parsed path segment
     */
    static UriPathSegment create(String pathSegment) {
        /*
        We can get:
        - empty string ("/")
        - text ("/plaintext")
        - encoded text ("/plain%20text")
        - text with matrix param(s) ("/plaintext;v=1.0;a;b=c,d")
         */
        Parameters pathParameters = UriMatrixParameters.create(pathSegment);
        String rawPathNoParams = UriPathHelper.stripMatrixParams(pathSegment);
        String decoded = UriEncoding.decodeUri(rawPathNoParams);
        return new UriPathSegmentImpl(pathSegment, rawPathNoParams, pathParameters, decoded);
    }

    /**
     * The text value of this path segment, without leading slash. This will return empty
     * string for path segments that do not have any text.
     *
     * @return decoded value of this path segment without matrix parameters
     */
    String value();

    /**
     * The raw text value (encoded) of this path segment.
     * @return encoded value of this path segment with matrix parameters
     */
    String rawValue();

    /**
     * The raw text value (encoded) of this path segment without matrix parameters.
     * @return encoded value of this path segment without matrix parameters
     */
    String rawValueNoParams();

    /**
     * All matrix parameters of this segment.
     *
     * @return matrix parameters
     */
    Parameters matrixParameters();
}
