/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.http2;

import java.util.LinkedList;
import java.util.List;

/**
 * HTTP/2 frame flag support.
 */
public class Http2Flag {
    /**
     * End of stream flag (used by headers, and data).
     */
    public static final int END_OF_STREAM = 0x1;
    /**
     * ACK flag, used for acknowledgements.
     */
    public static final int ACK = 0x1;
    /**
     * End of headers flag (used by headers, and continuations).
     */
    public static final int END_OF_HEADERS = 0x4;
    /**
     * Padded flag (used by headers, and data).
     */
    public static final int PADDED = 0x8;
    /**
     * Priority flag.
     */
    public static final int PRIORITY = 0x20;

    private final int value;

    Http2Flag(int value) {
        this.value = value;
    }

    /**
     * Flag value as a number.
     *
     * @return flag value (bit or of configured flags)
     */
    public int value() {
        return value;
    }

    @Override
    public String toString() {
        return value == 0 ? "" : String.valueOf(value);
    }

    private static boolean isSet(int flag, int value) {
        return (value & flag) != 0;
    }

    /**
     * Flags interface to have typed flag methods for types that support it.
     */
    public interface Flags {
        /**
         * Numeric flag value.
         *
         * @return flags
         */
        int value();
    }

    /**
     * Flags that support padding flag.
     */
    interface Padded extends Flags {
        /**
         * Whether the padded flag is set.
         *
         * @return is padded
         */
        default boolean padded() {
            return isSet(PADDED, value());
        }
    }

    /**
     * Flags that support ACK flag.
     */
    interface Ack extends Flags {
        /**
         * Whether the ack flag is set.
         *
         * @return is ack
         */
        default boolean ack() {
            return isSet(ACK, value());
        }
    }

    /**
     * Flags that support priority flag.
     */
    interface Priority extends Flags {
        /**
         * Whether the priority flag is set.
         *
         * @return is priority
         */
        default boolean priority() {
            return isSet(PRIORITY, value());
        }
    }

    /**
     * Flags that support end of headers flag.
     */
    interface EndOfHeaders extends Flags {
        /**
         * Whether the end of headers flag is set.
         *
         * @return is end of headers
         */
        default boolean endOfHeaders() {
            return isSet(END_OF_HEADERS, value());
        }
    }

    /**
     * Flags that support end of stream flag.
     */
    interface EndOfStream extends Flags {
        /**
         * Whether the end of stream flag is set.
         *
         * @return is end of stream
         */
        default boolean endOfStream() {
            return isSet(END_OF_STREAM, value());
        }
    }

    /**
     * Flags supported by headers frame.
     */
    public static class HeaderFlags extends Http2Flag implements EndOfStream, EndOfHeaders, Priority, Padded {
        private HeaderFlags(int value) {
            super(value);
        }

        /**
         * Create headers flags.
         *
         * @param flags flags number
         * @return header flags
         */
        public static HeaderFlags create(int flags) {
            return new HeaderFlags(flags & (END_OF_STREAM | END_OF_HEADERS | PRIORITY | PADDED));
        }

        @Override
        public String toString() {
            List<String> activeFlags = new LinkedList<>();
            if (endOfStream()) {
                activeFlags.add("END_STREAM");
            }
            if (endOfHeaders()) {
                activeFlags.add("END_HEADERS");
            }
            if (priority()) {
                activeFlags.add("PRIORITY");
            }
            if (padded()) {
                activeFlags.add("PADDED");
            }
            return String.join(", ", activeFlags);
        }
    }

    /**
     * Flags supported by data frame.
     */
    public static class DataFlags extends Http2Flag implements EndOfStream, Padded {
        private DataFlags(int value) {
            super(value);
        }

        /**
         * Create data flags.
         *
         * @param flags flags number
         * @return data flags
         */
        public static DataFlags create(int flags) {
            return new DataFlags(flags);
        }

        @Override
        public String toString() {
            List<String> activeFlags = new LinkedList<>();
            if (endOfStream()) {
                activeFlags.add("END_STREAM");
            }
            if (padded()) {
                activeFlags.add("PADDED");
            }
            return String.join(", ", activeFlags);
        }
    }

    /**
     * Flags supported by settings frame.
     */
    public static class SettingsFlags extends Http2Flag implements Ack {
        private SettingsFlags(int value) {
            super(value);
        }

        /**
         * Create settings flags.
         *
         * @param flags flags number
         * @return setting flags
         */
        public static SettingsFlags create(int flags) {
            return new SettingsFlags(flags & ACK); // we only support the ACK flag here, other must be excluded
        }

        @Override
        public String toString() {
            return (ack() ? "ACK" : "");
        }
    }

    /**
     * Flags supported by push promise frame.
     */
    public static class PushPromiseFlags extends Http2Flag implements EndOfHeaders, Padded {
        private PushPromiseFlags(int value) {
            super(value);
        }

        /**
         * Create push promise flags.
         *
         * @param flags flags number
         * @return push promise flags
         */
        public static PushPromiseFlags create(int flags) {
            return new PushPromiseFlags(flags);
        }
    }

    /**
     * Flags supported by ping frame.
     */
    public static class PingFlags extends Http2Flag implements Ack {
        private PingFlags(int value) {
            super(value);
        }

        /**
         * Create ping flags.
         *
         * @param flags flags number
         * @return ping flags
         */
        public static PingFlags create(int flags) {
            return new PingFlags(flags);
        }
    }

    /**
     * Flags supported by continuation frame.
     */
    public static class ContinuationFlags extends Http2Flag implements EndOfHeaders {
        private ContinuationFlags(int value) {
            super(value);
        }

        /**
         * Create continuation flags.
         *
         * @param flags flags number
         * @return continuation flags
         */
        public static ContinuationFlags create(int flags) {
            return new ContinuationFlags(flags & END_OF_HEADERS);
        }

        @Override
        public String toString() {
            return endOfHeaders() ? "END_HEADERS" : "";
        }
    }

    /**
     * Flags for frames that do not have support for any flag.
     */
    public static class NoFlags extends Http2Flag {
        private static final NoFlags EMPTY = new NoFlags(0);

        private NoFlags(int flags) {
            super(flags);
        }

        /**
         * Create no flags.
         *
         * @return no flags instance
         */
        public static NoFlags create() {
            return EMPTY;
        }

        /**
         * Create no flags from the numeric value (should be always zero, though not validated by this method).
         *
         * @param flags flags number
         * @return new no flags instance
         */
        public static NoFlags create(int flags) {
            return new NoFlags(flags);
        }
    }
}
