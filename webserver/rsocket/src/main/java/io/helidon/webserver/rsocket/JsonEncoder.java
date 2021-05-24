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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.rsocket.Payload;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.DefaultPayload;
import io.rsocket.util.EmptyPayload;

public class JsonEncoder implements Encoder {

    private static ObjectMapper mapper = new ObjectMapper();

    @Override
    public Payload encode(Object obj) {
        if (obj instanceof Payload) {
            return (Payload) obj;
        } else {
            // serialize object to payload
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                mapper.writeValue(stream, obj);
                return DefaultPayload.create(stream.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
                return EmptyPayload.INSTANCE;
            }
        }
    }

    @Override
    public <T> T decode(Payload payload, Class<T> cls) {
        if (Payload.class.isAssignableFrom(cls)) {
            return cls.cast(payload);
        }
        ByteBuffer data = payload.getData();
        try {
            return mapper.<T> readValue(data.array(), cls);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte getMimeTypeId() {
        return WellKnownMimeType.APPLICATION_JSON.getIdentifier();
    }

    @Override
    public String getMimeType() {
        return WellKnownMimeType.APPLICATION_JSON.getString();
    }

}