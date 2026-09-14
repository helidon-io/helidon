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

package io.helidon.webserver.observe.log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class LogServiceTest {

    @Test
    void testStreamWriteDoesNotForwardNestedLogRecords() throws Exception {
        List<String> forwarded = new ArrayList<>();
        Map<Object, Consumer<String>> listeners = new ConcurrentHashMap<>();
        LogService.LogMessageFilter filter = new LogService.LogMessageFilter(new MessageFormatter(), null, listeners);

        listeners.put(this, forwarded::add);

        OutputStreamWriter writer = new OutputStreamWriter(new LoggingOutputStream(filter), StandardCharsets.UTF_8);
        LogService.writeLogStream(writer, "stream message", true);

        assertThat(forwarded, empty());

        assertThat(filter.isLoggable(record("outside write")), is(true));
        assertThat(forwarded, contains("outside write"));
    }

    @Test
    void testStreamCloseDoesNotForwardNestedLogRecords() throws Exception {
        List<String> forwarded = new ArrayList<>();
        Map<Object, Consumer<String>> listeners = new ConcurrentHashMap<>();
        LogService.LogMessageFilter filter = new LogService.LogMessageFilter(new MessageFormatter(), null, listeners);

        listeners.put(this, forwarded::add);

        OutputStreamWriter writer = new OutputStreamWriter(new LoggingOutputStream(filter), StandardCharsets.UTF_8);
        LogService.closeLogStream(writer);

        assertThat(forwarded, empty());
    }

    @Test
    void testListenerRemovalDuringFireDoesNotFail() {
        List<String> forwarded = new ArrayList<>();
        Map<Object, Consumer<String>> listeners = new ConcurrentHashMap<>();
        LogService.LogMessageFilter filter = new LogService.LogMessageFilter(new MessageFormatter(), null, listeners);
        Object removingListener = new Object();

        listeners.put(removingListener, it -> listeners.remove(removingListener));
        listeners.put(this, forwarded::add);

        assertThat(filter.isLoggable(record("outside write")), is(true));
        assertThat(forwarded, contains("outside write"));
    }

    private static LogRecord record(String message) {
        return new LogRecord(Level.FINE, message);
    }

    private static final class MessageFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage();
        }
    }

    private static final class LoggingOutputStream extends OutputStream {
        private final LogService.LogMessageFilter filter;
        private boolean writeLogged;
        private boolean flushLogged;
        private boolean closeLogged;

        private LoggingOutputStream(LogService.LogMessageFilter filter) {
            this.filter = filter;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (!writeLogged) {
                writeLogged = true;
                log("nested write");
            }
        }

        @Override
        public void write(int b) throws IOException {
            if (!writeLogged) {
                writeLogged = true;
                log("nested write");
            }
        }

        @Override
        public void flush() {
            if (!flushLogged) {
                flushLogged = true;
                log("nested flush");
            }
        }

        @Override
        public void close() {
            if (!closeLogged) {
                closeLogged = true;
                log("nested close");
            }
        }

        private void log(String message) {
            assertThat(filter.isLoggable(record(message)), is(true));
        }
    }
}
