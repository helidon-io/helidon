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

package io.helidon.common.uri;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.parameters.Parameters;

/**
 * Abstraction of HTTP path supporting matrix parameters.
 * Note that matrix parameters are ONLY available on {@link #absolute()} path or on {@link #segments()}.
 */
public interface UriPath {
    /**
     * Creates a relative path from the provided path and the relative segment(s).
     *
     * @param uriPath used to obtain absolute path
     * @param relativePath relative segment, must not contain path parameters and is expected to be decoded
     * @return a new path representing the new relative path
     */
    static UriPath createRelative(UriPath uriPath, String relativePath) {
        return new UriPathNoParam(uriPath.absolute(), relativePath);
    }

    /**
     * Create a new path from its raw representation.
     *
     * @param rawPath raw path as received over the wire
     * @return a new HTTP path
     */
    static UriPath create(String rawPath) {
        String rawPathNoParams = UriPathHelper.stripMatrixParams(rawPath);
        if (rawPath.length() == rawPathNoParams.length()) {
            return new UriPathNoParam(rawPath);
        }
        return new UriPathMatrix(rawPath, rawPathNoParams);
    }

    /**
     * Create a new path from its decoded representation.
     *
     * @param decodedPath path as used in routing
     * @return a new HTTP path
     */
    static UriPath createFromDecoded(String decodedPath) {
        String pathNoParams = UriPathHelper.stripMatrixParams(decodedPath);
        if (pathNoParams.length() == decodedPath.length()) {
            return new UriPathNoParam(UriEncoding.encode(decodedPath, UriEncoding.Type.PATH));
        }
        return new UriPathMatrix(UriEncoding.encode(decodedPath, UriEncoding.Type.PATH),
                                 UriEncoding.encode(pathNoParams, UriEncoding.Type.PATH));
    }

    /**
     * Create a new root path.
     *
     * @return a new HTTP path
     */
    static UriPath root() {
        return create("/");
    }

    /**
     * Path as it was received on the wire (Server request), or path as it will be sent
     * over the wire (Client request). This path may include path parameters.
     *
     * @return path
     */
    String rawPath();

    /**
     * Path as it was received on the wire (Server request), or path as it will be sent
     * over the wire (Client request) WITHOUT matrix parameters.
     *
     * @return path
     */
    String rawPathNoParams();

    /**
     * Decoded path without matrix parameters.
     *
     * @return path without matrix parameters
     * @see #matrixParameters()
     */
    String path();

    /**
     * Path parameters collected from the full path.
     * Example of path with parameter: {@code /users;domain=work/john}. {@code domain} is the name of the parameter,
     *      {@code work} is value of the parameter.
     *
     * @return path parameters
     */
    Parameters matrixParameters();

    /**
     * If this instance represents a path relative to some context root then returns absolute requested path otherwise
     * returns this instance.
     * <p>
     * The absolute path also contains access to path parameters defined in context matchers. If there is
     * name conflict then value represents latest matcher result.
     *
     * @return an absolute requested URI path
     */
    UriPath absolute();

    /**
     * List of segments.
     * This is the most detailed access to the underlying path that provides raw, decoded access to path, access to
     * matrix parameters bound to each segment.
     * <p><b>NOTE:</b> this is an expensive method that requires full parsing of the path, please use with care
     *
     * @return list of URI path segments
     */
    default List<UriPathSegment> segments() {
        String[] segmentStrings = rawPath().split("/");
        List<UriPathSegment> segments = new ArrayList<>(segmentStrings.length);
        for (String segmentString : segmentStrings) {
            segments.add(UriPathSegment.create(segmentString));
        }

        return List.copyOf(segments);
    }

    /**
     * Validate if the raw path is valid.
     */
    void validate();
}
