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

package io.helidon.http;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.helidon.common.buffers.BufferData;

class HeaderValueCached extends HeaderValueBase {
    private final byte[] cached;
    private final String value;
    private final byte[] cachedHttp1Header;

    HeaderValueCached(HeaderName name, boolean changing, boolean sensitive, byte[] cached, String value) {
        super(name, changing, sensitive, value);

        this.value = value;
        this.cached = cached;

        byte[] nameBytes = name.defaultCase().getBytes(StandardCharsets.US_ASCII);
        cachedHttp1Header = new byte[nameBytes.length + cached.length + 4];
        int pos = nameBytes.length;
        System.arraycopy(nameBytes, 0, cachedHttp1Header, 0, pos);
        cachedHttp1Header[pos++] = ':';
        cachedHttp1Header[pos++] = ' ';
        System.arraycopy(cached, 0, cachedHttp1Header, pos, cached.length);
        pos += cached.length;
        cachedHttp1Header[pos++] = '\r';
        cachedHttp1Header[pos] = '\n';
    }

    @Override
    public byte[] valueBytes() {
        return cached;
    }

    @Override
    public void writeHttp1Header(BufferData buffer) {
        buffer.write(cachedHttp1Header);
    }

    @Override
    public List<String> allValues() {
        return List.of(value);
    }

    @Override
    public int valueCount() {
        return 1;
    }
}
