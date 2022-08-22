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

class UriPathSegmentImpl implements UriPathSegment {
    private final String rawPath;
    private final String rawPathNoParams;
    private final Parameters matrixParameters;
    private final String decoded;

    UriPathSegmentImpl(String rawPath, String rawPathNoParams, Parameters matrixParameters, String decoded) {
        this.rawPath = rawPath;
        this.rawPathNoParams = rawPathNoParams;
        this.matrixParameters = matrixParameters;
        this.decoded = decoded;
    }

    @Override
    public String value() {
        return decoded;
    }

    @Override
    public String rawValue() {
        return rawPath;
    }

    @Override
    public String rawValueNoParams() {
        return rawPathNoParams;
    }

    @Override
    public Parameters matrixParameters() {
        return matrixParameters;
    }
}
