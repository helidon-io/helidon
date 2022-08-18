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

import java.net.URI;
import java.util.List;

import io.helidon.common.parameters.Parameters;

class UriPathNoParam implements UriPath {
    private static final Parameters EMPTY_PARAMS = Parameters.empty("path");

    private final UriPath absolute;
    private final String rawPath;
    private String decodedPath;
    private List<UriPathSegment> segments;

    UriPathNoParam(String rawPath) {
        this.rawPath = rawPath;
        this.absolute = this;
    }

    UriPathNoParam(UriPath absolute, String relativePath) {
        this.rawPath = relativePath;
        this.decodedPath = relativePath;
        this.absolute = absolute;
    }

    @Override
    public String rawPath() {
        return rawPath;
    }

    @Override
    public String rawPathNoParams() {
        return rawPath;
    }

    @Override
    public String path() {
        if (decodedPath == null) {
            decodedPath = decode(rawPath);
        }
        return decodedPath;
    }

    @Override
    public Parameters matrixParameters() {
        return EMPTY_PARAMS;
    }

    @Override
    public UriPath absolute() {
        return absolute;
    }

    @Override
    public List<UriPathSegment> segments() {
        if (segments == null) {
            segments = UriPath.super.segments();
        }
        return segments;
    }

    @Override
    public String toString() {
        return rawPath;
    }

    @Override
    public void validate() {
        if (decodedPath == null) {
            this.decodedPath = URI.create(rawPath).normalize().getPath();
        }
    }

    private static String decode(String rawPath) {
        /*
        Raw path may:
         - be encoded (%20)
         - contain // that need to be resolved into /
         - be relative with /./ and /../ - these need to be normalized

         we use decoded and normalized path to match routing, so we need to resolve all of that
         */
        int percent = rawPath.indexOf('%');
        int dot = rawPath.indexOf(".");
        int doubleSlash = rawPath.indexOf("//");

        if (percent == -1 && doubleSlash == -1 && dot == -1) {
            return rawPath;
        }

        return URI.create(rawPath).normalize().getPath();
    }
}
