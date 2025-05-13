/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http.encoding.gzip;

import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.spi.ContentEncodingProvider;

/**
 * Support for gzip content encoding.
 */
public class GzipEncodingProvider implements ContentEncodingProvider, Weighted {
    /**
     * Default constructor required by Java {@link java.util.ServiceLoader}.
     */
    public GzipEncodingProvider() {
    }

    @Override
    public String configKey() {
        return "gzip";
    }

    @Override
    public ContentEncoding create(Config config, String name) {
        return new GzipEncoding(name);
    }

    @Override
    public double weight() {
        // this has a high weight, as gzip is supported by most clients and server
        return Weighted.DEFAULT_WEIGHT + 100;
    }
}
