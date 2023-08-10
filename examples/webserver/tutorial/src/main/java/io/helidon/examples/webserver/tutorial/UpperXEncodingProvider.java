/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.tutorial;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;

/**
 * All 'x' must be upper case.
 * <p>
 * This is a naive implementation.
 */
public final class UpperXEncodingProvider implements ContentEncoding {

    @Override
    public Set<String> ids() {
        return Set.of("upper-x");
    }

    @Override
    public boolean supportsEncoding() {
        return false;
    }

    @Override
    public boolean supportsDecoding() {
        return true;
    }

    @Override
    public ContentDecoder decoder() {
        return UpperXInputStream::new;
    }

    @Override
    public ContentEncoder encoder() {
        return ContentEncoder.NO_OP;
    }

    @Override
    public String name() {
        return "upper-x";
    }

    @Override
    public String type() {
        return "upper-x";
    }

    /**
     * All 'x' must be upper case.
     * <p>
     * This is a naive implementation.
     */
    static final class UpperXInputStream extends FilterInputStream {

        UpperXInputStream(InputStream is) {
            super(is);
        }

        @Override
        public int read() throws IOException {
            int c = super.read();
            return c == 'x' ? 'X' : c;
        }
    }
}
