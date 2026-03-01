/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import io.helidon.http.HttpException;
import io.helidon.http.Status;

abstract class EntityIoBase {
    private static final System.Logger LOGGER = System.getLogger(EntityIoBase.class.getName());

    /**
     * Convert charset (probably from Content-Type or Accept-Charset header) to {@link java.nio.charset.Charset}.
     *
     * @param charset charset string
     * @return charset instance
     * @throws io.helidon.http.HttpException with 415 status if the charset cannot be parsed
     */
    static Charset charset(String charset) {
        try {
            return Charset.forName(charset);
        } catch (UnsupportedCharsetException e) {
            // we cannot fail without a good exception on wrong charset, as this would end
            // in 500 error and be logged
            LOGGER.log(System.Logger.Level.TRACE, "Unsupported charset: {0} in media type", charset);
            throw new HttpException("Unsupported charset", Status.UNSUPPORTED_MEDIA_TYPE_415, e);
        }
    }

}
