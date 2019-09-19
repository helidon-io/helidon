/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.media.type.spi;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detect media type.
 * Minimal implementation checks result based on file suffix.
 */
public interface MediaTypeDetector {
    default Optional<String> detectType(URL url) {
        return detectType(url.getPath());
    }

    default Optional<String> detectType(URI uri) {
        return detectType(uri.getPath());
    }

    default Optional<String> detectType(Path file) {
        return detectType(file.getFileName().toString());
    }

    default Optional<String> detectType(String fileString) {
        // file string - we are interested in last . index
        int index = fileString.lastIndexOf('.');

        String inProgress = fileString;
        if (index > -1) {
            inProgress = inProgress.substring(index + 1);
        } else {
            return Optional.empty();
        }

        // and now it should be safe - just a suffix
        return detectExtensionType(inProgress);
    }

    Optional<String> detectExtensionType(String fileSuffix);
}
