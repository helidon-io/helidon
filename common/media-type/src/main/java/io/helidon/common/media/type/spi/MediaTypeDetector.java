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

package io.helidon.common.media.type.spi;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detect media type.
 * Minimal implementation checks result based on file suffix.
 * <p>
 * If suffix is not sufficient, you can use {@link #detectType(String)}, or implement each of the
 * methods.
 */
public interface MediaTypeDetector {
    /**
     * Detect type based on a {@link java.net.URL}.
     * Default implementation uses the {@link java.net.URL#getPath()} and invokes the {@link #detectType(String)}.
     *
     * @param url to find media type from
     * @return media type if detected
     */
    default Optional<String> detectType(URL url) {
        return detectType(url.getPath());
    }

    /**
     * Detect type based on a {@link java.net.URL}.
     * Default implementation uses the {@link java.net.URI#getPath()} and invokes the {@link #detectType(String)}.
     *
     * @param uri to find media type from
     * @return media type if detected
     */
    default Optional<String> detectType(URI uri) {
        return detectType(uri.getPath());
    }

    /**
     * Detect type based on a {@link java.nio.file.Path}.
     * Default implementation uses the {@link java.nio.file.Path#getFileName()} as a String
     * and invokes the {@link #detectType(String)}.
     *
     * @param file to find media type from
     * @return media type if detected
     */
    default Optional<String> detectType(Path file) {
        Path fileName = file.getFileName();
        if (null == fileName) {
            return Optional.empty();
        }
        return detectType(fileName.toString());
    }

    /**
     * Detect type based on a file string. This may be any string that ends with a file name.
     *
     * Default implementation looks for a suffix (last part of the file string separated by a dot) and if found,
     *  invokes the {@link #detectExtensionType(String)}.
     *
     * @param fileString path, or name of the file to analyze
     * @return media type if detected
     */
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

    /**
     * Detect media type from a file suffix.
     * <p>Example:
     * If the suffix is {@code txt}, this method should return {@code text/plain} if this detector cares
     * about text files.
     *
     * @param fileSuffix suffix (extension) of a file, without the leading dot
     * @return media type if detected
     */
    Optional<String> detectExtensionType(String fileSuffix);
}
