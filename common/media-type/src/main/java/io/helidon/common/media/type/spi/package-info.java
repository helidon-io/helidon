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
/**
 * SPI to provide custom mappings of resources to media types.
 * <p>
 * In addition to the Java service loader interface {@link io.helidon.common.media.type.spi.MediaTypeDetector}, you can
 * also provide a file localed in {@code META-INF/media-types.properties} on the classpath to override or add media type
 * mappings.
 * <p>
 * The property file only supports mapping of file extensions to media types.
 * <p>
 * Example:
 * <pre>
 * ooo=application/ooo
 * </pre>
 */
package io.helidon.common.media.type.spi;
