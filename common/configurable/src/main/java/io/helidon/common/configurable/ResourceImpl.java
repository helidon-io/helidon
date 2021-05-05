/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link Resource}.
 */
class ResourceImpl implements Resource {
    private static final Logger LOGGER = Logger.getLogger(Resource.class.getName());

    private final Resource.Source source;
    private final String location;
    private final InputStream stream;

    private volatile boolean streamObtained;
    private volatile byte[] cachedBytes;

    ResourceImpl(Resource.Source resourceSource, String location, byte[] bytes) {
        this.source = resourceSource;
        this.location = location;
        this.stream = new ByteArrayInputStream(bytes);
        this.cachedBytes = bytes;
    }

    ResourceImpl(Resource.Source resourceSource, String location, InputStream stream) {
        this.source = resourceSource;
        this.location = location;
        this.stream = stream;
    }

    @Override
    public InputStream stream() {
        check();
        streamObtained = true;
        if (null == cachedBytes) {
            return stream;
        } else {
            return new ByteArrayInputStream(cachedBytes);
        }
    }

    @Override
    public byte[] bytes() {
        check();
        cacheBytes();
        return cachedBytes;
    }

    @Override
    public String string() {
        check();
        cacheBytes();

        return new String(cachedBytes, StandardCharsets.UTF_8);
    }

    @Override
    public String string(Charset charset) {
        check();
        cacheBytes();

        return new String(cachedBytes, charset);
    }

    @Override
    public Source sourceType() {
        return source;
    }

    @Override
    public String location() {
        return location;
    }

    @Override
    public void cacheBytes() {
        if (cachedBytes == null) {
            try {
                cachedBytes = stream.readAllBytes();
            } catch (IOException e) {
                throw new ResourceException("Failed to fully read resource bytes for resource: " + source + "("
                                                    + location + ")", e);
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                               "Failed to close input stream for resource: " + source + "(" + location + ")");
                }
            }
        }
    }

    private void check() {
        if (streamObtained && (cachedBytes == null)) {
            throw new IllegalStateException(
                    "Once you get the stream, you cannot call other methods on this resource:" + source + " ("
                            + location + ")");
        }
    }

    @Override
    public String toString() {
        return "Resource { source='" + source + "',"
                + "location='" + location + "'}";
    }

}
