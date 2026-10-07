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

package io.helidon.http.media.multipart;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiPartMaterializationContextTest {
    private static final int PART_LIMIT = 8;
    private static final String OUTER_BOUNDARY = "outer-boundary";
    private static final String INNER_BOUNDARY = "inner-boundary";
    private static final Headers HEADERS = WritableHeaders.create()
            .set(HeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + OUTER_BOUNDARY);
    private static final Headers REQUEST_HEADERS = WritableHeaders.create();
    private static final MediaContext MEDIA_CONTEXT = MediaContext.builder()
            .mediaSupportsDiscoverServices(false)
            .addMediaSupport(MultiPartSupport.create(Config.empty()))
            .build();

    @Test
    void nestedPartsHaveIndependentInheritedBudgets() {
        for (boolean clientResponse : new boolean[] {false, true}) {
            String side = clientResponse ? "Client response" : "Server request";
            RuntimeException failure = limitFailure(clientResponse);
            var entity = new ParentEntity(nestedMultipart(PART_LIMIT, PART_LIMIT), failure, clientResponse);
            MultiPart outer = entity.as(MultiPart.class);
            assertThat(side + " contains a nested multipart part", outer.hasNext(), is(true));
            MultiPart nested = outer.next().as(MultiPart.class);

            int count = 0;
            while (nested.hasNext()) {
                assertThat(side + " exact-cap nested part", nested.next().as(String.class), is("A".repeat(PART_LIMIT)));
                count++;
            }
            assertThat(side + " materialized both exact-cap inner parts", count, is(2));
            assertThat(side + " exhausted outer multipart", outer.hasNext(), is(false));
        }
    }

    @Test
    void nestedOversizedPartRetainsOriginalLimitFailure() {
        for (boolean clientResponse : new boolean[] {false, true}) {
            String side = clientResponse ? "Client response" : "Server request";
            RuntimeException failure = limitFailure(clientResponse);
            var entity = new ParentEntity(nestedMultipart(PART_LIMIT + 1), failure, clientResponse);
            MultiPart outer = entity.as(MultiPart.class);
            assertThat(side + " contains a nested multipart part", outer.hasNext(), is(true));
            MultiPart nested = outer.next().as(MultiPart.class);
            assertThat(side + " contains an oversized nested part", nested.hasNext(), is(true));
            ReadablePart part = nested.next();

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> part.as(String.class),
                                                   side + " must reject oversized nested materialization");
            assertThat(side + " retains its parent failure policy", thrown, sameInstance(failure));
        }
    }

    private static RuntimeException limitFailure(boolean clientResponse) {
        return clientResponse
                ? new IllegalStateException("Client materialized entity limit")
                : new HttpException("Server materialized entity limit", Status.REQUEST_ENTITY_TOO_LARGE_413, false);
    }

    private static byte[] nestedMultipart(int... partSizes) {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < partSizes.length; i++) {
            content.append("--").append(INNER_BOUNDARY).append("\r\n")
                    .append("Content-Disposition: form-data; name=inner-").append(i).append("\r\n")
                    .append("Content-Type: text/plain\r\n\r\n")
                    .append("A".repeat(partSizes[i])).append("\r\n");
        }
        content.append("--").append(INNER_BOUNDARY).append("--\r\n");
        String payload = "--" + OUTER_BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=nested\r\n"
                + "Content-Type: multipart/form-data; boundary=" + INNER_BOUNDARY + "\r\n\r\n"
                + content + "\r\n--" + OUTER_BOUNDARY + "--\r\n";
        return payload.getBytes(StandardCharsets.US_ASCII);
    }

    private static final class ParentEntity extends ReadableEntityBase {
        private final RuntimeException failure;
        private final boolean clientResponse;

        private ParentEntity(byte[] payload, RuntimeException failure, boolean clientResponse) {
            super(new Source(payload), () -> { }, PART_LIMIT);
            this.failure = failure;
            this.clientResponse = clientResponse;
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            throw new UnsupportedOperationException("Test fixture does not copy");
        }

        @Override
        protected <T> T entityAs(GenericType<T> type) {
            return clientResponse
                    ? readEntity(MEDIA_CONTEXT.reader(type, REQUEST_HEADERS, HEADERS), type, REQUEST_HEADERS, HEADERS)
                    : readEntity(MEDIA_CONTEXT.reader(type, HEADERS), type, HEADERS);
        }

        @Override
        protected RuntimeException materializedEntityLimitExceeded() {
            return failure;
        }
    }

    private static final class Source implements Function<Integer, BufferData> {
        private final byte[] payload;
        private boolean supplied;

        private Source(byte[] payload) {
            this.payload = payload;
        }

        @Override
        public BufferData apply(Integer estimate) {
            if (supplied) {
                return BufferData.empty();
            }
            supplied = true;
            return BufferData.create(payload);
        }
    }
}
