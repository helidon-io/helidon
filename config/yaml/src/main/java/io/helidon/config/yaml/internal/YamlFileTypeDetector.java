/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.yaml.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

/**
 * Custom implementation of Java {@link FileTypeDetector} to support YAML format.
 */
public class YamlFileTypeDetector extends FileTypeDetector {
    @Override
    public String probeContentType(Path path) throws IOException {
        String resourceUri = path.toString();
        if (resourceUri.indexOf('.') != -1) {
            String fileExt = resourceUri.substring(resourceUri.lastIndexOf('.'));
            if (fileExt.equals(".yaml")) {
                return YamlConfigParser.MEDIA_TYPE_APPLICATION_YAML;
            }
        }
        return null;
    }
}
