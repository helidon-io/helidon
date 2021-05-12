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

import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.common.media.type.spi.MediaTypeDetector;

/**
 * Example implementing a base name handling.
 */
public class DockerfileTypeDetector implements MediaTypeDetector {
    static String MEDIA_TYPE = "application/dockerfile";

    private static final Pattern DOCKERFILE_PATTERN =
            Pattern.compile(".*Dockerfile(\\.\\w+)?$");

    @Override
    public Optional<String> detectType(String fileString) {
        if (DOCKERFILE_PATTERN.matcher(fileString).matches()) {
            return Optional.of(MEDIA_TYPE);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> detectExtensionType(String fileSuffix) {
        return Optional.empty();
    }
}
