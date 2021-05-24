/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.rsocket;

import io.rsocket.Payload;

public class ProxyEncoder implements Encoder {
    @Override
    public Payload encode(Object obj) {
        if (obj instanceof Payload)
            return (Payload) obj;
        return null;
    }

    @Override
    public <T> T decode(Payload payload, Class<T> cls) {
        if (Payload.class.isAssignableFrom(cls)) {
            return cls.cast(payload);
        }
        return null;
    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public byte getMimeTypeId() {
        return 0;
    }
}