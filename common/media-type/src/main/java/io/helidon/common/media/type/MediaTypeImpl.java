/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.media.type;

import java.util.Optional;

record MediaTypeImpl(String type, String subtype, String text) implements MediaType {

    private static final System.Logger LOGGER = System.getLogger(MediaTypeImpl.class.getName());

    static MediaType parse(String fullType, ParserMode parserMode) {
        int slashIndex = fullType.indexOf('/');
        if (slashIndex < 1) {
            if (parserMode == ParserMode.RELAXED) {
                Optional<MediaType> maybeRelaxedType = ParserMode.findRelaxedMediaType(fullType);
                if (maybeRelaxedType.isPresent()) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               () -> String.format("Invalid media type value \"%s\" replaced with \"%s\"",
                                                   fullType,
                                                   maybeRelaxedType.get().text()));
                    return maybeRelaxedType.get();
                }
            }
            throw new IllegalArgumentException("Cannot parse media type: " + fullType);
        }
        return new MediaTypeImpl(fullType.substring(0, slashIndex),
                                 fullType.substring(slashIndex + 1),
                                 fullType);
    }

    @Override
    public String toString() {
        return text();
    }
}
