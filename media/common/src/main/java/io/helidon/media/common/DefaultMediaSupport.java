/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.common;

import java.util.Collection;
import java.util.List;

/**
 * MediaSupport which registers default readers and writers to the contexts.
 */
public class DefaultMediaSupport implements MediaSupport {

    private final boolean includeStackTraces;

    private DefaultMediaSupport(boolean includeStackTraces) {
        this.includeStackTraces = includeStackTraces;
    }

    /**
     * Creates new instance of {@link DefaultMediaSupport}.
     *
     * @param includeStackTraces include stack traces
     * @return new service instance
     */
    public static DefaultMediaSupport create(boolean includeStackTraces) {
        return new DefaultMediaSupport(includeStackTraces);
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(StringBodyReader.create(),
                       InputStreamBodyReader.create());
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(CharSequenceBodyWriter.create(),
                       ByteChannelBodyWriter.create(),
                       PathBodyWriter.create(),
                       FileBodyWriter.create(),
                       ThrowableBodyWriter.create(includeStackTraces));
    }
}
