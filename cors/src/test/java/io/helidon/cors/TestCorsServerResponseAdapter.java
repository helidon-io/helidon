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
package io.helidon.cors;

import io.helidon.http.HeaderName;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;

class TestCorsServerResponseAdapter implements CorsResponseAdapter<TestCorsServerResponseAdapter> {

    private final ServerResponseHeaders headers = ServerResponseHeaders.create();
    private int status;

    @Override
    public CorsResponseAdapter<TestCorsServerResponseAdapter> header(HeaderName key, String value) {
        headers.add(key, value);
        return this;
    }

    @Override
    public CorsResponseAdapter<TestCorsServerResponseAdapter> header(HeaderName key, Object value) {
        headers.add(key, value.toString());
        return this;
    }

    @Override
    public TestCorsServerResponseAdapter forbidden(String message) {
        status = Status.FORBIDDEN_403.code();
        return this;
    }

    @Override
    public TestCorsServerResponseAdapter ok() {
        status = Status.OK_200.code();
        return this;
    }

    @Override
    public int status() {
        return status;
    }
}
