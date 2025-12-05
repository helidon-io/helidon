/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.io.InputStream;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.media.ReadableEntity;

class Http2ServerRequestNoEntity implements ReadableEntity {

    private static final Http2ServerRequestNoEntity INSTANCE = new Http2ServerRequestNoEntity();

    static Http2ServerRequestNoEntity create() {
        return INSTANCE;
    }

    @Override
    public InputStream inputStream() {
        return EmptyInputStream.INSTANCE;
    }

    @Override
    public <T> T as(GenericType<T> type) {
        return null;
    }

    @Override
    public <T> Optional<T> asOptional(GenericType<T> type) {
        return Optional.empty();
    }

    @Override
    public boolean hasEntity() {
        return false;
    }

    @Override
    public boolean consumed() {
        return true;
    }

    @Override
    public void consume() {
        //noop
    }

    @Override
    public ReadableEntity copy(Runnable entityProcessedRunnable) {
        return this;
    }

    private static class EmptyInputStream extends InputStream {

        static final InputStream INSTANCE = new EmptyInputStream();

        @Override
        public int available() {
            return 0;
        }

        public int read() {
            return -1;
        }
    }
}
