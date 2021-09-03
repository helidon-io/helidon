/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.net.URL;
import java.util.Optional;

import io.helidon.common.media.type.spi.MediaTypeDetector;

/**
 * Unit test detector.
 */
public class CustomTypeDetector implements MediaTypeDetector {
    static final String SUFFIX = "mine";
    static final String MEDIA_TYPE = "application/mine";
    static final String MEDIA_TYPE_HTTP = "application/http-mine";

    @Override
    public Optional<String> detectType(URL url) {
        if (url.getPath().endsWith("." + SUFFIX)) {
            if (url.getProtocol().equals("http")) {
                return Optional.of(MEDIA_TYPE_HTTP);
            }
            return Optional.of(MEDIA_TYPE);
        }

        return Optional.empty();
    }

    @Override
    public Optional<String> detectExtensionType(String fileSuffix) {
        if (SUFFIX.equals(fileSuffix)) {
            return Optional.of(MEDIA_TYPE);
        }
        return Optional.empty();
    }
}
