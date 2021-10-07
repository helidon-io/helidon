/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parser for multipart MIME message.
 */
final class MimeParser {

    /**
     * The emitted parser event types.
     */
    enum EventType {

        /**
         * This event is the first event issued by the parser.
         * It is generated only once.
         */
        START_MESSAGE,

        /**
         * This event is issued when a new part is detected.
         * It is generated for each part.
         */
        START_PART,

        /**
         * This event is issued for each header line of a part. It may be
         * generated more than once for each part.
         */
        HEADER,

        /**
         * This event is issued for each header line of a part. It may be
         * generated more than once for each part.
         */
        END_HEADERS,

        /**
         * This event is used by the Iterator to pass the whole body.
         */
        BODY,

        /**
         * This event is issued when the content for a part is complete.
         * It is generated only once for each part.
         */
        END_PART,

        /**
         * This event is issued when all parts are complete. It is generated
         * only once.
         */
        END_MESSAGE,
    }

    /**
     * Base class for the parser events.
     */
    abstract static class ParserEvent {

        /**
         * Get the event type.
         * @return EVENT_TYPE
         */
        abstract EventType type();

        /**
         * Get this event as a {@link HeaderEvent}.
         * @return HeaderEvent
         */
        HeaderEvent asHeaderEvent() {
            return (HeaderEvent) this;
        }

        /**
         * Get this event as a {@link BodyEvent}.
         * @return HeaderEvent
         */
        BodyEvent asBodyEvent() {
            return (BodyEvent) this;
        }
    }

    /**
     * The event class for {@link EventType#START_MESSAGE}.
     */
    static final class StartMessageEvent extends ParserEvent {

        private StartMessageEvent() {
        }

        @Override
        EventType type() {
            return EventType.START_MESSAGE;
        }
    }

    /**
     * The event class for {@link EventType#START_MESSAGE}.
     */
    static final class StartPartEvent extends ParserEvent {

        private StartPartEvent() {
        }

        @Override
        EventType type() {
            return EventType.START_PART;
        }
    }

    /**
     * The event class for {@link EventType#HEADER}.
     */
    static final class HeaderEvent extends ParserEvent {

        private final String name;
        private final String value;

        private HeaderEvent(String name, String value) {
            this.name = name;
            this.value = value;
        }

        String name() {
            return name;
        }

        String value() {
            return value;
        }

        @Override
        EventType type() {
            return EventType.HEADER;
        }
    }

    /**
     * The event class for {@link EventType#END_HEADERS}.
     */
    static final class EndHeadersEvent extends ParserEvent {

        private EndHeadersEvent() {
        }

        @Override
        EventType type() {
            return EventType.END_HEADERS;
        }
    }

    /**
     * The event class for {@link EventType#BODY}.
     */
    static final class BodyEvent extends ParserEvent {

        private final List<VirtualBuffer.BufferEntry> buffers;

        BodyEvent(List<VirtualBuffer.BufferEntry> data) {
            this.buffers = data;
        }

        List<VirtualBuffer.BufferEntry> body() {
            return buffers;
        }

        @Override
        EventType type() {
            return EventType.BODY;
        }
    }

    /**
     * The event class for {@link EventType#END_PART}.
     */
    static final class EndPartEvent extends ParserEvent {

        private EndPartEvent() {
        }

        @Override
        EventType type() {
            return EventType.END_PART;
        }
    }

    /**
     * The event class for {@link EventType#END_MESSAGE}.
     */
    static final class EndMessageEvent extends ParserEvent {

        private EndMessageEvent() {
        }

        @Override
        EventType type() {
            return EventType.END_MESSAGE;
        }
    }

    /**
     * MIME Parsing exception.
     */
    static final class ParsingException extends RuntimeException {

        /**
         * Create a new exception with the specified message.
         * @param message exception message
         */
        private ParsingException(String message) {
            super(message);
        }

        /**
         * Create a new exception with the specified cause.
         * @param cause exception cause
         */
        private ParsingException(Throwable cause) {
            super(cause);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MimeParser.class.getName());
    private static final Charset HEADER_ENCODING = StandardCharsets.ISO_8859_1;

    /**
     * All states.
     */
    private enum STATE {
        START_MESSAGE,
        SKIP_PREAMBLE,
        START_PART,
        HEADERS,
        BODY,
        END_PART,
        END_MESSAGE,
        DATA_REQUIRED
    }

    private static final StartMessageEvent START_MESSAGE_EVENT = new StartMessageEvent();
    private static final StartPartEvent START_PART_EVENT = new StartPartEvent();
    private static final EndHeadersEvent END_HEADERS_EVENT = new EndHeadersEvent();
    private static final EndPartEvent END_PART_EVENT = new EndPartEvent();
    private static final EndMessageEvent END_MESSAGE_EVENT = new EndMessageEvent();

    /**
     * The current parser state.
     */
    private STATE state = STATE.START_MESSAGE;

    /**
     * The parser state to resume to, non {@code null} when {@link #state} is
     * equal to {@link STATE#DATA_REQUIRED}.
     */
    private STATE resumeState = null;

    /**
     * Boundary as bytes.
     */
    private final byte[] bndbytes;

    /**
     * Boundary length.
     */
    private final int bl;

    /**
     * BnM algorithm: Bad Character Shift table.
     */
    private final int[] bcs = new int[128];

    /**
     * BnM algorithm : Good Suffix Shift table.
     */
    private final int[] gss;

    /**
     * Read and process body partsList until we see the terminating boundary
     * line.
     */
    private boolean done = false;

    /**
     * Beginning of the line.
     */
    private boolean bol;

    /**
     * Read-only byte array of the current byte buffer being processed.
     */
    private final VirtualBuffer buf;

    /**
     * The current parsing position in the buffer.
     */
    private int position;

    /**
     * The position of the next boundary.
     */
    private int bndStart;

    /**
     * Indicates if this parser is closed.
     */
    private boolean closed;

    /**
     * Parses the MIME content.
     */
    MimeParser(String boundary) {
        bndbytes = getBytes("--" + boundary);
        bl = bndbytes.length;
        gss = new int[bl];
        buf = new VirtualBuffer();
        compileBoundaryPattern();
    }

    /**
     * Push new data to the parsing buffer.
     *
     * @param data new data add to the parsing buffer
     * @throws ParsingException if the parser state is not consistent
     * @return buffer id
     */
    int offer(ByteBuffer data) throws ParsingException {
        if (closed) {
            throw new ParsingException("Parser is closed");
        }
        int id;
        switch (state) {
            case START_MESSAGE:
                id = buf.offer(data, 0);
                break;
            case DATA_REQUIRED:
                // resume the previous state
                state = resumeState;
                resumeState = null;
                id = buf.offer(data, position);
                position = 0;
                break;
            default:
                throw new ParsingException("Invalid state: " + state);
        }
        return id;
    }

    /**
     * Mark this parser instance as closed. Invoking this method indicates that
     * no more data will be pushed to the parsing buffer.
     *
     * @throws ParsingException if the parser state is not {@code END_MESSAGE}
     * or {@code START_MESSAGE}
     */
    void close() throws ParsingException {
        cleanup();
        switch (state) {
            case START_MESSAGE:
            case END_MESSAGE:
                break;
            case DATA_REQUIRED:
                switch (resumeState) {
                    case SKIP_PREAMBLE:
                        throw new ParsingException("Missing start boundary");
                    case BODY:
                        throw new ParsingException("No closing MIME boundary");
                    case HEADERS:
                        throw new ParsingException("No blank line found");
                    default:
                        // do nothing
                }
                break;
            default:
                throw new ParsingException("Invalid state: " + state);
        }
    }

    /**
     * Like close(), but just releases resources and does not throw.
     */
    void cleanup() {
        closed = true;
        buf.clear();
    }

    /**
     * Advances parsing.
     * @throws ParsingException if an error occurs during parsing
     */
    Iterator<ParserEvent> parseIterator() {
        return new Iterator<>() {

            private ParserEvent nextEvent;
            private boolean done;

            @Override
            public ParserEvent next() {
                if (!hasNext()) {
                    throw new IllegalStateException("Read past end of stream");
                }
                ParserEvent ne = nextEvent;
                nextEvent = null;
                done = ne == END_MESSAGE_EVENT;
                return ne;
            }

            @Override
            public boolean hasNext() {
                if (nextEvent != null) {
                    return true;
                }

                try {
                    while (true) {
                        switch (state) {
                            case START_MESSAGE:
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER, "state={0}", STATE.START_MESSAGE);
                                }
                                state = STATE.SKIP_PREAMBLE;
                                nextEvent = START_MESSAGE_EVENT;
                                return true;

                            case SKIP_PREAMBLE:
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER, "state={0}", STATE.SKIP_PREAMBLE);
                                }
                                skipPreamble();
                                if (bndStart == -1) {
                                    if (LOGGER.isLoggable(Level.FINER)) {
                                        LOGGER.log(Level.FINER, "state={0}", STATE.DATA_REQUIRED);
                                    }
                                    state = STATE.DATA_REQUIRED;
                                    resumeState = STATE.SKIP_PREAMBLE;
                                    return false;
                                }
                                if (LOGGER.isLoggable(Level.FINE)) {
                                    LOGGER.log(Level.FINE, "Skipped the preamble. position={0}", position);
                                }
                                state = STATE.START_PART;
                                break;

                            // fall through
                            case START_PART:
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER, "state={0}", STATE.START_PART);
                                }
                                state = STATE.HEADERS;
                                nextEvent = START_PART_EVENT;
                                return true;

                            case HEADERS:
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER, "state={0}", STATE.HEADERS);
                                }
                                String headerLine = readHeaderLine();
                                if (headerLine == null) {
                                    if (LOGGER.isLoggable(Level.FINER)) {
                                        LOGGER.log(Level.FINER, "state={0}", STATE.DATA_REQUIRED);
                                    }
                                    state = STATE.DATA_REQUIRED;
                                    resumeState = STATE.HEADERS;
                                    return false;
                                }
                                if (!headerLine.isEmpty()) {
                                    Hdr header = new Hdr(headerLine);
                                    nextEvent = new HeaderEvent(header.name(), header.value());
                                    return true;
                                }
                                state = STATE.BODY;
                                bol = true;
                                nextEvent = END_HEADERS_EVENT;
                                return true;

                            case BODY:
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER, "state={0}", STATE.BODY);
                                }
                                List<VirtualBuffer.BufferEntry> bodyContent = readBody();
                                if (bndStart == -1 || bodyContent.isEmpty()) {
                                    if (LOGGER.isLoggable(Level.FINER)) {
                                        LOGGER.log(Level.FINER, "state={0}", STATE.DATA_REQUIRED);
                                    }
                                    state = STATE.DATA_REQUIRED;
                                    resumeState = STATE.BODY;
                                    if (bodyContent.isEmpty()) {
                                        return false;
                                    }
                                } else {
                                    bol = false;
                                }
                                nextEvent = new BodyEvent(bodyContent);
                                return true;

                            case END_PART:
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER, "state={0}", STATE.END_PART);
                                }
                                if (MimeParser.this.done) {
                                    state = STATE.END_MESSAGE;
                                } else {
                                    state = STATE.START_PART;
                                }
                                nextEvent = END_PART_EVENT;
                                return true;

                            case END_MESSAGE:
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER, "state={0}", STATE.END_MESSAGE);
                                }
                                if (done) {
                                    return false;
                                }
                                nextEvent = END_MESSAGE_EVENT;
                                return true;

                            case DATA_REQUIRED:
                                return false;

                            default:
                                // nothing to do
                        }
                    }
                } catch (ParsingException ex) {
                    throw ex;
                } catch (Throwable ex) {
                    throw new ParsingException(ex);
                }
            }
        };
    }

    /**
     * Reads the next part body content.
     *
     * @return list of read-only ByteBuffer, or empty list if more data is
     * required and no body content can be returned.
     */
    private List<VirtualBuffer.BufferEntry> readBody() {
        // matches boundary
        bndStart = match();
        int bufLen = buf.length();
        if (bndStart == -1) {
            // No boundary is found
            if (position + bl + 1 < bufLen) {
                // there may be an incomplete boundary at the end of the buffer
                // return the remaining data minus the boundary length
                // so that it can be processed next iteration
                int bodyBegin = position;
                position = bufLen - (bl + 1);
                return buf.slice(bodyBegin, position);
            }
            // remaining data can be an complete boundary, force it to be
            // processed during next iteration
            return Collections.emptyList();
        }

        // Found boundary.
        // Is it at the start of a line ?
        int bodyEnd = bndStart;
        if (bol && bndStart == position) {
            // nothing to do
        } else if (bndStart > position
                && (buf.getByte(bndStart - 1) == '\n' || buf.getByte(bndStart - 1) == '\r')) {
            --bodyEnd;
            if (buf.getByte(bndStart - 1) == '\n'
                    && bndStart > 1 && buf.getByte(bndStart - 2) == '\r') {
                --bodyEnd;
            }
        } else {
            // boundary is not at beginning of a line
            int bodyBegin = position;
            position = bodyEnd + 1;
            return buf.slice(bodyBegin, position);
        }

        // check if this is a "closing" boundary
        if (bndStart + bl + 1 < bufLen
                && buf.getByte(bndStart + bl) == '-'
                && buf.getByte(bndStart + bl + 1) == '-') {

            state = STATE.END_PART;
            done = true;
            int bodyBegin = position;
            position = bndStart + bl + 2;
            return buf.slice(bodyBegin, bodyEnd);
        }

        // Consider all the linear whitespace in boundary+whitespace+"\r\n"
        int lwsp = 0;
        for (int i = bndStart + bl; i < bufLen
                && (buf.getByte(i) == ' ' || buf.getByte(i) == '\t'); i++) {
            ++lwsp;
        }

        // Check boundary+whitespace+"\n"
        if (bndStart + bl + lwsp < bufLen
                && buf.getByte(bndStart + bl + lwsp) == '\n') {

            state = STATE.END_PART;
            int bodyBegin = position;
            position = bndStart + bl + lwsp + 1;
            return buf.slice(bodyBegin, bodyEnd);
        }

        // Check for boundary+whitespace+"\r\n"
        if (bndStart + bl + lwsp + 1 < bufLen
                && buf.getByte(bndStart + bl + lwsp) == '\r'
                && buf.getByte(bndStart + bl + lwsp + 1) == '\n') {

            state = STATE.END_PART;
            int bodyBegin = position;
            position = bndStart + bl + lwsp + 2;
            return buf.slice(bodyBegin, bodyEnd);
        }

        if (bndStart + bl + lwsp + 1 < bufLen) {
            // boundary string in a part data
            int bodyBegin = position;
            position = bodyEnd + 1;
            return buf.slice(bodyBegin, bodyEnd + 1);
        }

        // A boundary is found but it's not a "closing" boundary
        // return everything before that boundary as the "closing" characters
        // might be available next iteration
        int bodyBegin = position;
        position = bndStart;
        return buf.slice(bodyBegin, bodyEnd);
    }

    /**
     * Skips the preamble.
     */
    private void skipPreamble() {
        // matches boundary
        bndStart = match();
        if (bndStart == -1) {
            // No boundary is found
            return;
        }

        int bufLen = buf.length();

        // Consider all the whitespace boundary+whitespace+"\r\n"
        int lwsp = 0;
        for (int i = bndStart + bl; i < bufLen
                && (buf.getByte(i) == ' ' || buf.getByte(i) == '\t'); i++) {
            ++lwsp;
        }

        // Check for \n or \r\n
        if (bndStart + bl + lwsp < bufLen
                && (buf.getByte(bndStart + bl + lwsp) == '\n'
                || buf.getByte(bndStart + bl + lwsp) == '\r')) {

            if (buf.getByte(bndStart + bl + lwsp) == '\n') {
                position = bndStart + bl + lwsp + 1;
                return;
            } else if (bndStart + bl + lwsp + 1 < bufLen
                    && buf.getByte(bndStart + bl + lwsp + 1) == '\n') {
                position = bndStart + bl + lwsp + 2;
                return;
            }
        }
        position = bndStart + 1;
    }

    /**
     * Read the lines for a single header.
     *
     * @return a header line or an empty string if the blank line separating the
     * header from the body has been reached, or {@code null} if the there is
     * no more data in the buffer
     */
    private String readHeaderLine() {
        // FIXME: what about multi-line headers?
        int bufLen = buf.length();
        // need more data to progress
        // need at least one blank line to read (no headers)
        if (position >= bufLen - 1) {
            return null;
        }
        int offset = position;
        int hdrLen = 0;
        int lwsp = 0;
        for (; offset + hdrLen < bufLen; hdrLen++) {
            if (buf.getByte(offset + hdrLen) == '\n') {
                lwsp += 1;
                break;
            }
            if (offset + hdrLen + 1 >= bufLen) {
                // No more data in the buffer
                return null;
            }
            if (buf.getByte(offset + hdrLen) == '\r'
                    && buf.getByte(offset + hdrLen + 1) == '\n') {
                lwsp += 2;
                break;
            }
        }
        position = offset + hdrLen + lwsp;
        if (hdrLen == 0){
            return "";
        }
        return new String(buf.getBytes(offset, hdrLen), HEADER_ENCODING);
    }

    /**
     * Boyer-Moore search method.
     * Copied from {@link java.util.regex.Pattern}
     *
     * Pre calculates arrays needed to generate the bad character shift and the
     * good suffix shift. Only the last seven bits are used to see if chars
     * match; This keeps the tables small and covers the heavily used ASCII
     * range, but occasionally results in an aliased match for the bad character
     * shift.
     */
    private void compileBoundaryPattern() {
        int i;
        int j;

        // Precalculate part of the bad character shift
        // It is a table for where in the pattern each
        // lower 7-bit value occurs
        for (i = 0; i < bndbytes.length; i++) {
            bcs[bndbytes[i] & 0x7F] = i + 1;
        }

        // Precalculate the good suffix shift
        // i is the shift amount being considered
        NEXT:
        for (i = bndbytes.length; i > 0; i--) {
            // j is the beginning index of suffix being considered
            for (j = bndbytes.length - 1; j >= i; j--) {
                // Testing for good suffix
                if (bndbytes[j] == bndbytes[j - i]) {
                    // src[j..len] is a good suffix
                    gss[j - 1] = i;
                } else {
                    // No match. The array has already been
                    // filled up with correct values before.
                    continue NEXT;
                }
            }
            // This fills up the remaining of optoSft
            // any suffix can not have larger shift amount
            // then its sub-suffix. Why???
            while (j > 0) {
                gss[--j] = i;
            }
        }
        // Set the guard value because of unicode compression
        gss[bndbytes.length - 1] = 1;
    }

    /**
     * Finds the boundary in the given buffer using Boyer-Moore algorithm.
     * Copied from {@link java.util.regex.Pattern}
     *
     * @return -1 if there is no match or index where the match starts
     */
    private int match() {
        int last = buf.length() - bndbytes.length;
        int off = position;

        // Loop over all possible match positions in text
        NEXT:
        while (off <= last) {
            // Loop over pattern from right to left
            for (int j = bndbytes.length - 1; j >= 0; j--) {
                byte ch = buf.getByte(off + j);
                if (ch != bndbytes[j]) {
                    // Shift search to the right by the maximum of the
                    // bad character shift and the good suffix shift
                    off += Math.max(j + 1 - bcs[ch & 0x7F], gss[j]);
                    continue NEXT;
                }
            }
            // Entire pattern matched starting at off
            return off;
        }
        return -1;
    }

    /**
     * Get the bytes representation of a string.
     * @param str string to convert
     * @return byte[]
     */
    private static byte[] getBytes(String str) {
        char[] chars = str.toCharArray();
        int size = chars.length;
        byte[] bytes = new byte[size];

        for (int i = 0; i < size;) {
            bytes[i] = (byte) chars[i++];
        }
        return bytes;
    }

    /**
     * A private utility class to represent an individual header.
     */
    private static final class Hdr {

        /**
         * The trimmed name of this header.
         */
        private final String name;

        /**
         * The entire header "line".
         */
        private final String line;

        /**
         * Constructor that takes a line and splits out the header name.
         */
        Hdr(String l) {
            int i = l.indexOf(':');
            if (i < 0) {
                // should never happen
                name = l.trim();
            } else {
                name = l.substring(0, i).trim();
            }
            line = l;
        }

        /**
         * Return the "name" part of the header line.
         */
        String name() {
            return name;
        }

        /**
         * Return the "value" part of the header line.
         */
        String value() {
            int i = line.indexOf(':');
            if (i < 0) {
                return line;
            }

            int j;
            // skip whitespace after ':'
            for (j = i + 1; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!(c == ' ' || c == '\t')) {
                    break;
                }
            }
            return line.substring(j);
        }
    }
}
