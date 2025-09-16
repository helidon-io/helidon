/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.metadata;

import java.io.InputStream;

/**
 * A single metadata file, such as {@code META-INF/helidon/service.loader}.
 */
public interface MetadataFile {
    /**
     * Name of the file.
     *
     * @return file name, such as {@code service.loader}
     */
    String fileName();

    /**
     * Input stream to read the underlying resource.
     *
     * @return stream to the resource
     */
    InputStream inputStream();

    /**
     * Classpath location of this resource.
     *
     * @return classpath location, such as {@code META-INF/helidon/service.loader}
     */
    String location();

    /**
     * Absolute location of this resource, such as absolute path on the file system, or on the classpath.
     *
     * @return location, such as the URL of the resource as string
     */
    String absoluteLocation();
}
