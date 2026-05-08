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
package io.helidon.codegen;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.codegen.JavadocReader.CdataImpl;
import io.helidon.codegen.JavadocReader.CommentImpl;
import io.helidon.codegen.JavadocReader.DoctypeImpl;
import io.helidon.codegen.JavadocReader.EltCloseImpl;
import io.helidon.codegen.JavadocReader.EscapeImpl;
import io.helidon.codegen.JavadocReader.TextImpl;
import io.helidon.codegen.JavadocTree.AttrValue;
import io.helidon.codegen.JavadocTree.AttrValue.Kind;

/**
 * Javadoc parser.
 */
class JavadocParser implements Iterator<JavadocParser.Event> {

    private final char[] buf;
    private int position;
    private int valueEndPos;
    private int valueStartPos;
    private int lineNo = 1;
    private int charNo = 0;
    private int lc = 0;
    private State lastState = State.START;
    private State state = State.START;
    private Event event;
    private Event lastEvent;

    JavadocParser(String str) {
        buf = str.toCharArray();
    }

    @Override
    public boolean hasNext() {
        while (position < buf.length && event == null) {
            char c = buf[position];
            if (c == '\n') {
                lineNo++;
                charNo = 1;
            }
            valueEndPos = position;
            switch (state) {
                case START -> handleStart();
                case LINE -> handleLine();
                case TOKEN -> handleToken();
                case TEXT -> handleText();
                case DOCTYPE -> handle(Handler.DOCTYPE);
                case CDATA -> handle(Handler.CDATA);
                case COMMENT -> handle(Handler.COMMENT);
                case ATTRIBUTE -> handle(Handler.ATTRIBUTE);
                case SINGLE_QUOTE -> handle(Handler.SINGLE_QUOTE);
                case DOUBLE_QUOTE -> handle(Handler.DOUBLE_QUOTE);
                case UNQUOTED -> handle(Handler.UNQUOTED);
                case ELT_START -> handle(Handler.ELT_START);
                case ELT_END -> handle(Handler.ELT_END);
                case INLINE_TAG -> handle(Handler.INLINE_TAG);
                case INLINE_NAME -> handleInlineTagName();
                case INLINE_BODY_TEXT -> handleInlineBodyText();
                case INLINE_BODY_REF -> handle(Handler.INLINE_BODY_REF);
                case INLINE_CURLY -> handleInlineCurly();
                case BLOCK_TAG -> handle(Handler.BLOCK_TAG);
                case BREAK -> {
                    event = Event.STOPPER;
                    nextState(State.TEXT);
                    valueStartPos = ++position;
                }
                case ESCAPE -> {
                    event = new EscapeImpl(String.valueOf(c));
                    valueStartPos = ++position;
                    nextState(State.TEXT);
                }
                default -> {
                }
            }
            charNo += (position - valueEndPos);
        }
        return event != null;
    }

    @Override
    public Event next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        lastEvent = event;
        event = null;
        return lastEvent;
    }

    String cursor() {
        return "line: %d, col: %d".formatted(lineNo, charNo);
    }

    Event peek() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return event;
    }

    void skip() {
        lastEvent = event;
        event = null;
    }

    private void handleToken() {
        boolean foundToken = false;
        for (var token : Token.values()) {
            if (token.reader.apply(this)) {
                valueStartPos = position;
                switch (token) {
                    case LOWER_THAN -> nextState(State.ELT_START);
                    case GREATER_THAN -> {
                        event = Event.STOPPER;
                        nextState(State.TEXT);
                    }
                    case SELF_CLOSE -> {
                        event = Event.SELF_CLOSE;
                        nextState(State.TEXT);
                    }
                    case CLOSE -> nextState(State.ELT_END);
                    case EQUAL -> nextState(State.UNQUOTED);
                    case SINGLE_QUOTE -> nextState(State.SINGLE_QUOTE);
                    case DOUBLE_QUOTE -> nextState(State.DOUBLE_QUOTE);
                    case DOCTYPE -> nextState(State.DOCTYPE);
                    case COMMENT -> nextState(State.COMMENT);
                    case CDATA -> nextState(State.CDATA);
                    case LINE -> nextState(State.LINE);
                    default -> {
                        // ignore
                    }
                }
                foundToken = true;
                break;
            }
        }
        if (!foundToken) {
            valueStartPos = position;
            switch (lastState) {
                case ELT_START,
                     ATTRIBUTE,
                     SINGLE_QUOTE,
                     DOUBLE_QUOTE -> nextState(State.ATTRIBUTE);
                default -> nextState(State.TEXT);
            }
        }
    }

    private void handle(Handler handler) {
        if (handler.predicate.test(this)) {
            int offset = handler.offset;
            position += offset;
            if (valueEndPos > valueStartPos) {
                event = handler.factory.apply(value());
                valueStartPos = position;
            }
            nextState(handler.nextState);
        } else if (++position >= buf.length) {
            valueEndPos = position;
            event = handler.factory.apply(value());
        }
    }

    private void handleStart() {
        while (position < buf.length) {
            char c = buf[position];
            if (!Character.isWhitespace(c)) {
                nextState(State.LINE);
                valueStartPos = position;
                break;
            } else {
                position++;
            }
        }
    }

    private void handleLine() {
        if (position + 1 < buf.length) {
            if (consumeChar('@')) {

                // escapes...
                if (readChar('@', '*', '/')) {
                    emit();
                    nextState(State.ESCAPE);
                    return;
                }

                // block tag
                skipTrailingWhitespaces();
                emit();
                nextState(State.BLOCK_TAG);
                return;
            }
        }

        // strip leading whitespaces
        if (consumeWhiteSpace()) {
            if (position == buf.length) {
                emitLast();
            }
            return;
        }

        nextState(State.TEXT);
    }

    private void handleText() {
        if (position + 2 < buf.length) {
            if (consumeString("{@")) {

                // escapes...
                if (readChar('@')) {
                    valueEndPos = position - 1;
                    emit();
                    nextState(State.ESCAPE);
                    return;
                }

                // inline tag
                emit();
                nextState(State.INLINE_TAG);
                return;
            }
        }

        // HTML
        if (readChar('<')) {
            emit();
            nextState(State.TOKEN);
            return;
        }

        // any character
        char c = buf[position++];

        if (position == buf.length) {
            emitLast();
            return;
        }

        if (c == '\n') {
            nextState(State.LINE);
        }
    }

    private void handleInlineTagName() {
        if (lastEvent instanceof Event.InlineTag(var s)) {
            switch (s) {
                case "link", "linkplain", "value" -> nextState(State.INLINE_BODY_REF);
                default -> nextState(State.INLINE_BODY_TEXT);
            }
        } else {
            throw new IllegalStateException("Unable to handle inline tag name, " + cursor());
        }
    }

    private void handleInlineBodyText() {
        char c = buf[position];
        switch (c) {
            case '{' -> {
                lc = 0;
                nextState(State.INLINE_CURLY);
            }
            case '}' -> {
                if (valueEndPos > valueStartPos) {
                    if (Character.isWhitespace(buf[valueStartPos])) {
                        valueStartPos++;
                    }
                    event = new TextImpl(value());
                }
                nextState(State.BREAK);
            }
            default -> position++;
        }
    }

    private void handleInlineCurly() {
        char c = buf[position];
        if (c == '{') {
            lc++;
        } else if (c == '}' && --lc == 0) {
            nextState(State.INLINE_BODY_TEXT);
        }
        position++;
    }

    private void emit() {
        if (valueEndPos > valueStartPos) {
            event = new TextImpl(value());
        }
        valueStartPos = position;
    }

    private void emitLast() {
        valueEndPos++;
        skipTrailingWhitespaces();
        emit();
    }

    private String value() {
        return String.valueOf(buf, valueStartPos, valueEndPos - valueStartPos);
    }

    private void nextState(State nextState) {
        lastState = state;
        state = nextState;
    }

    private void skipTrailingWhitespaces() {
        if (valueEndPos > 1) {
            while (Character.isWhitespace(buf[valueEndPos - 1])) {
                valueEndPos--;
            }
        }
    }

    private boolean consumeWhiteSpace() {
        char c = buf[position];
        if (Character.isWhitespace(c)) {
            position++;
            return true;
        }
        return false;
    }

    private boolean consumeChar(char expected) {
        char c = buf[position];
        if (c == expected) {
            position++;
            return true;
        }
        return false;
    }

    private boolean consumeString(String str) {
        if (readString(str)) {
            position += str.length();
            return true;
        }
        return false;
    }

    private boolean readChar(char... chars) {
        for (char c : chars) {
            if (buf[position] == c) {
                return true;
            }
        }
        return false;
    }

    private boolean readString(String str) {
        if (position + str.length() < buf.length + 1) {
            return str.equals(String.valueOf(buf, position, str.length()));
        }
        return false;
    }

    private boolean readWhiteSpace() {
        char c = buf[position];
        return Character.isWhitespace(c);
    }

    private boolean readEndAttribute() {
        char c = buf[position];
        return switch (c) {
            case '"', '\'', '>', '/', '=' -> true;
            default -> Character.isWhitespace(c);
        };
    }

    private boolean readEndUnquoted() {
        char c = buf[position];
        return switch (c) {
            case '"', '\'', '=', '/', '<', '>', '`' -> true;
            default -> Character.isWhitespace(c);
        };
    }

    private boolean readEndElement() {
        char c = buf[position];
        return switch (c) {
            case '>', '/' -> true;
            default -> Character.isWhitespace(c);
        };
    }

    private boolean readEndInlineTag() {
        char c = buf[position];
        return switch (c) {
            case '}', '{' -> true;
            default -> Character.isWhitespace(c);
        };
    }

    /**
     * Parser event.
     */
    @SuppressWarnings("checkstyle:InterfaceIsType")
    sealed interface Event permits JavadocTree.EltClose,
                                   JavadocTree.Text,
                                   JavadocTree.Escape,
                                   JavadocTree.Comment,
                                   JavadocTree.Cdata,
                                   AttrValue,
                                   JavadocTree.Doctype,
                                   Event.EltStart,
                                   Event.SelfClose,
                                   Event.Stopper,
                                   Event.AttrName,
                                   Event.InlineTag,
                                   Event.BlockTag {

        /**
         * Constant for {@link Stopper}.
         */
        Event STOPPER = new Stopper();

        /**
         * Constant for {@link SelfClose}.
         */
        Event SELF_CLOSE = new SelfClose();

        /**
         * HTML attribute name.
         *
         * @param name name
         */
        record AttrName(String name) implements Event {
        }

        /**
         * HTML element start.
         *
         * @param name name
         */
        record EltStart(String name) implements Event {
        }

        /**
         * End of attribute or inline tag.
         */
        record Stopper() implements Event {
        }

        /**
         * HTML element self close.
         */
        record SelfClose() implements Event {
        }

        /**
         * Inline tag.
         *
         * @param tag tag
         */
        record InlineTag(String tag) implements Event {
        }

        /**
         * Block tag.
         *
         * @param tag tag
         */
        record BlockTag(String tag) implements Event {
        }
    }



    private enum Token {
        LINE('\n'),
        WHITESPACE(JavadocParser::consumeWhiteSpace),
        DOCTYPE("<!DOCTYPE"),
        CDATA("<![CDATA["),
        COMMENT("<!--"),
        CLOSE("</"),
        SELF_CLOSE("/>"),
        LOWER_THAN('<'),
        GREATER_THAN('>'),
        SINGLE_QUOTE("='"),
        DOUBLE_QUOTE("=\""),
        EQUAL('=');

        private final Function<JavadocParser, Boolean> reader;

        Token(Function<JavadocParser, Boolean> function) {
            reader = function;
        }

        Token(char ch) {
            reader = t -> t.consumeChar(ch);
        }

        Token(String str) {
            reader = t -> t.consumeString(str);
        }
    }

    private enum Handler {
        DOCTYPE(p -> p.readChar('>'), 1, s -> new DoctypeImpl(s.strip()), State.TOKEN),
        CDATA(p -> p.readString("]]>"), 3, CdataImpl::new, State.TOKEN),
        COMMENT(p -> p.readString("-->"), 3, CommentImpl::new, State.TOKEN),
        ATTRIBUTE(JavadocParser::readEndAttribute, 0, Event.AttrName::new, State.TOKEN),
        SINGLE_QUOTE(p -> p.readChar('\''), 1, s -> new AttrValue(s, Kind.SINGLE), State.TOKEN),
        DOUBLE_QUOTE(p -> p.readChar('"'), 1, s -> new AttrValue(s, Kind.DOUBLE), State.TOKEN),
        UNQUOTED(JavadocParser::readEndUnquoted, 0, s -> new AttrValue(s, Kind.UNQUOTED), State.TOKEN),
        ELT_START(JavadocParser::readEndElement, 0, Event.EltStart::new, State.TOKEN),
        ELT_END(JavadocParser::readEndElement, 1, EltCloseImpl::new, State.TEXT),
        INLINE_TAG(JavadocParser::readEndInlineTag, 0, Event.InlineTag::new, State.INLINE_NAME),
        INLINE_BODY_REF(p -> p.readChar('}'), 0, s -> new TextImpl(s.strip()), State.BREAK),
        BLOCK_TAG(JavadocParser::readWhiteSpace, 1, Event.BlockTag::new, State.TOKEN);

        private final Predicate<JavadocParser> predicate;
        private final int offset;
        private final Function<String, Event> factory;
        private final State nextState;

        Handler(Predicate<JavadocParser> predicate,
                int offset,
                Function<String, Event> factory,
                State nextState) {

            this.predicate = predicate;
            this.offset = offset;
            this.factory = factory;
            this.nextState = nextState;
        }
    }

    private enum State {
        START,
        TOKEN,
        LINE,
        TEXT,
        DOCTYPE,
        CDATA,
        COMMENT,
        ELT_START,
        ATTRIBUTE,
        UNQUOTED,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        BREAK,
        ELT_END,
        INLINE_BODY_TEXT,
        INLINE_BODY_REF,
        INLINE_CURLY,
        INLINE_NAME,
        INLINE_TAG,
        BLOCK_TAG,
        ESCAPE
    }
}
