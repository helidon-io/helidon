/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.tyrus;

import java.util.logging.Logger;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

/**
 * Class UppercaseCodec.
 */
public class UppercaseCodec implements Decoder.Text<String>, Encoder.Text<String> {
    private static final Logger LOGGER = Logger.getLogger(UppercaseCodec.class.getName());

    private static final String ENCODING_PREFIX = "\0\0";

    public UppercaseCodec() {
        LOGGER.info("UppercaseCodec instance created");
    }

    @Override
    public String decode(String s) {
        LOGGER.info("UppercaseCodec decode called");
        return ENCODING_PREFIX + s;
    }

    @Override
    public boolean willDecode(String s) {
        return true;
    }

    @Override
    public void init(EndpointConfig config) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public String encode(String s) {
        LOGGER.info("UppercaseCodec encode called");
        return s.replace(ENCODING_PREFIX, "");
    }

    public static boolean isDecoded(String s) {
        return s.startsWith(ENCODING_PREFIX);
    }
}
