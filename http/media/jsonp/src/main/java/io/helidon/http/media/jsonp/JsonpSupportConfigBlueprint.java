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

package io.helidon.http.media.jsonp;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.media.spi.MediaSupportProvider;

import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriterFactory;

/**
 * Configuration of the {@link JsonpSupport}.
 */
@Prototype.Blueprint(decorator = JsonpSupport.Decorator.class)
@Prototype.Provides(MediaSupportProvider.class)
interface JsonpSupportConfigBlueprint extends Prototype.Factory<JsonpSupport> {

    /**
     * Name of the support. Default value is {@code jsonp}.
     *
     * @return name of the support
     */
    @Option.Default("jsonp")
    String name();

    /**
     * Jsonp reader factory.
     *
     * @return reader factory
     */
    JsonReaderFactory readerFactory();

    /**
     * Jsonp writer factory.
     *
     * @return writer factory
     */
    JsonWriterFactory writerFactory();

}
