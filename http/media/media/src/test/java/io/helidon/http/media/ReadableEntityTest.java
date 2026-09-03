/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.http.media;

import java.io.InputStream;
import java.util.Optional;

import io.helidon.common.GenericType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ReadableEntityTest {

    @Test
    void defaultOptionalClassConversionUsesGenericOptionalConversion() {
        ReadableEntity entity = new CustomReadableEntity();

        assertThat(entity.asOptional(String.class), is(Optional.of("ok")));
    }

    private static final class CustomReadableEntity implements ReadableEntity {
        @Override
        public InputStream inputStream() {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public <T> T as(GenericType<T> type) {
            throw new IllegalStateException("Generic non-optional conversion must not be used");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> asOptional(GenericType<T> type) {
            return (Optional<T>) Optional.of("ok");
        }

        @Override
        public boolean hasEntity() {
            return true;
        }

        @Override
        public boolean consumed() {
            return false;
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }
}
