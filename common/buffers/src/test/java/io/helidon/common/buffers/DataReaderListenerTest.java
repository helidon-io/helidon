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

package io.helidon.common.buffers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataReaderListenerTest {
    @Test
    void lookaheadAndConsumptionReportEachChunkOnce() {
        byte[] first = "first\r".getBytes(StandardCharsets.US_ASCII);
        byte[] second = "\nsecond".getBytes(StandardCharsets.US_ASCII);
        var chunks = List.of(first, second).iterator();
        var reader = DataReader.create(() -> chunks.hasNext() ? chunks.next() : null);
        var listener = new RecordingListener();

        reader.listener(listener, "input");
        assertThat(listener.notifications, empty());
        assertThat(reader.findNewLine(20), is(5));
        assertThat(reader.lookup(), is((byte) 'f'));
        assertThat(reader.startsWith("first".getBytes(StandardCharsets.US_ASCII)), is(true));
        assertThat(reader.readLine(), is("first"));
        assertThat(reader.read(), is((byte) 's'));
        assertThat(reader.readLazyString(StandardCharsets.US_ASCII, 2).toString(), is("ec"));
        assertThat(reader.readBuffer(2).readString(2, StandardCharsets.US_ASCII), is("on"));
        assertThat(reader.readBytes(1), is(new byte[] {'d'}));

        assertThat(listener.notifications, contains("input:first\r", "input:\nsecond"));
        assertThat(listener.chunks, contains(sameInstance(first), sameInstance(second)));
        assertThrows(DataReader.InsufficientDataAvailableException.class, reader::read);
        assertThat(listener.notifications, contains("input:first\r", "input:\nsecond"));
    }

    @Test
    void firstListenerReceivesOnlyBufferedUnreadBytesWithoutFetching() {
        byte[] first = "first".getBytes(StandardCharsets.US_ASCII);
        byte[] second = "second".getBytes(StandardCharsets.US_ASCII);
        var chunks = List.of(first, second).iterator();
        var pulls = new AtomicInteger();
        var reader = DataReader.create(() -> {
            pulls.incrementAndGet();
            return chunks.hasNext() ? chunks.next() : null;
        });
        assertThat(reader.readAsciiString(2), is("fi"));
        assertThat(reader.getBuffer(6).readString(6, StandardCharsets.US_ASCII), is("rstsec"));
        var listener = new RecordingListener();

        reader.listener(listener, "prefetched");

        assertThat(pulls.get(), is(2));
        assertThat(reader.available(), is(9));
        assertThat(listener.notifications, contains("prefetched:rst", "prefetched:second"));
        assertThat(listener.chunks, contains(sameInstance(first), sameInstance(second)));
        reader.skip(9);
        assertThat(listener.notifications, contains("prefetched:rst", "prefetched:second"));
    }

    @Test
    void firstListenerDoesNotReplayFullyConsumedChunks() {
        var chunks = List.of("first".getBytes(StandardCharsets.US_ASCII),
                             "second".getBytes(StandardCharsets.US_ASCII)).iterator();
        var reader = DataReader.create(() -> chunks.hasNext() ? chunks.next() : null);
        assertThat(reader.readAsciiString(5), is("first"));
        var listener = new RecordingListener();

        reader.listener(listener, "input");
        assertThat(listener.notifications, empty());
        assertThat(reader.readAsciiString(6), is("second"));

        assertThat(listener.notifications, contains("input:second"));
    }

    @Test
    void replacementReceivesOnlyFutureChunksWithNewContext() {
        var chunks = List.of("first".getBytes(StandardCharsets.US_ASCII),
                             "second".getBytes(StandardCharsets.US_ASCII)).iterator();
        var reader = DataReader.create(() -> chunks.hasNext() ? chunks.next() : null);
        var original = new RecordingListener();
        var replacement = new RecordingListener();
        reader.listener(original, "original");
        assertThat(reader.lookup(), is((byte) 'f'));

        reader.listener(replacement, "replacement");
        assertThat(replacement.notifications, empty());
        assertThat(reader.readAsciiString(5), is("first"));
        assertThat(replacement.notifications, empty());
        reader.pullData();
        assertThat(reader.readBuffer().readString(6, StandardCharsets.US_ASCII), is("second"));

        assertThat(original.notifications, contains("original:first"));
        assertThat(replacement.notifications, contains("replacement:second"));
    }

    @Test
    void emptyChunksDoNotNotifyListener() {
        var chunks = List.of(new byte[0], "data".getBytes(StandardCharsets.US_ASCII)).iterator();
        var reader = DataReader.create(() -> chunks.hasNext() ? chunks.next() : null);
        var listener = new RecordingListener();
        reader.listener(listener, "input");

        assertThat(reader.readAsciiString(4), is("data"));

        assertThat(listener.notifications, contains("input:data"));
    }

    @Test
    void rejectsNullListenerOrContextBeforeChangingRegistration() {
        var reader = DataReader.create(() -> "data".getBytes(StandardCharsets.US_ASCII));
        var listener = new RecordingListener();
        reader.pullData();

        assertThrows(NullPointerException.class, () -> reader.listener(null, "input"));
        assertThrows(NullPointerException.class, () -> reader.listener(listener, null));
        reader.listener(listener, "input");

        assertThat(listener.notifications, contains("input:data"));
    }

    private static class RecordingListener implements DataListener<String> {
        private final List<String> notifications = new ArrayList<>();
        private final List<byte[]> chunks = new ArrayList<>();

        @Override
        public void data(String context, byte[] data, int position, int length) {
            notifications.add(context + ":" + new String(data, position, length, StandardCharsets.US_ASCII));
            chunks.add(data);
        }
    }
}
