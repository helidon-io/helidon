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

package io.helidon.http.encoding;

import java.io.InputStream;
import java.util.function.UnaryOperator;

/**
 * Content decoder.
 */
@FunctionalInterface
public interface ContentDecoder extends UnaryOperator<InputStream> {
    /**
     * No op content decoder.
     */
    ContentDecoder NO_OP = network -> network;

    /**
     * Decode a network input stream.
     *
     * @param network bytes as travelling over the network
     * @return plain input stream (decompressed, decrypted)
     */
    @Override
    InputStream apply(InputStream network);
}
