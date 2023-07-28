/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.micrometer;

import io.helidon.metrics.api.Tag;

class MicrometerTag implements Tag {

    static MicrometerTag of(String key, String value) {
        return new MicrometerTag(io.micrometer.core.instrument.Tag.of(key, value));
    }

    static MicrometerTag of(io.micrometer.core.instrument.Tag mTag) {
        return of(mTag.getKey(), mTag.getValue());
    }

    private final io.micrometer.core.instrument.Tag delegate;

    private MicrometerTag(io.micrometer.core.instrument.Tag delegate) {
        this.delegate = delegate;
    }

    @Override
    public String key() {
        return delegate.getKey();
    }

    @Override
    public String value() {
        return delegate.getValue();
    }
}
