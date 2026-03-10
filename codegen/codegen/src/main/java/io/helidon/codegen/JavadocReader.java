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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.codegen.JavadocParser.Event;
import io.helidon.codegen.JavadocTree.AttrValue;
import io.helidon.codegen.JavadocTree.BlockTag;
import io.helidon.codegen.JavadocTree.Document;
import io.helidon.codegen.JavadocTree.Text;

/**
 * Javadoc reader.
 * <p>
 * <b>This class is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
public final class JavadocReader {

    /**
     * Create a new instance.
     *
     * @param input input
     * @return reader
     */
    public static JavadocReader create(String input) {
        return new JavadocReader(input);
    }

    private final JavadocParser parser;

    private JavadocReader(String input) {
        this.parser = new JavadocParser(input);
    }

    /**
     * Parse the document.
     *
     * @return document, never {@code null}
     */
    public Document read() {
        var fs = new ArrayList<JavadocTree>();
        var body = new ArrayList<JavadocTree>();
        var elements = fs;
        var blockTags = new ArrayList<BlockTag>();
        while (parser.hasNext()) {
            var event = parser.next();
            switch (event) {
                case Event.EltStart(var s) -> {
                    var attrs = readAttributes();
                    boolean selfClosing = false;
                    if (parser.hasNext() && parser.peek() instanceof Event.SelfClose) {
                        selfClosing = true;
                        parser.skip();
                    }
                    var e = new EltStartImpl(s, selfClosing, attrs);
                    if (elements == fs && !elements.isEmpty() && isSentenceBreak(s)) {
                        elements = body;
                    }
                    elements.add(e);
                }
                case Event.InlineTag(var s) -> {
                    var e = new InlineTagImpl(s, readInlineBody());
                    switch (s) {
                        case "return", "summary" -> {
                            elements.add(e);
                            if (elements == fs) {
                                elements = body;
                            }
                        }
                        default -> elements.add(e);
                    }
                }
                case Event.BlockTag(var s) -> blockTags.add(new BlockTagImpl(s, readBlockBody()));
                case Text n -> {
                    if (elements == fs) {
                        var s = n.value();
                        int sbreak  = sentenceBreak(s);
                        if (sbreak > 0) {
                            fs.add(new TextImpl(s.substring(0, sbreak).stripTrailing()));
                            int offset = skipWhiteSpace(s, sbreak);
                            if (offset > 0) {
                                body.add(new TextImpl(s.substring(offset)));
                            }
                            elements = body;
                            break;
                        }
                    }
                    elements.add(n);
                }
                case JavadocTree e -> elements.add(e);
                default -> {
                    // ignore
                }
            }
        }
        return new DocumentImpl(
                Collections.unmodifiableList(fs),
                Collections.unmodifiableList(body),
                Collections.unmodifiableList(blockTags));
    }

    private List<JavadocTree> readBlockBody() {
        List<JavadocTree> body = null;
        while (parser.hasNext()) {
            var event = parser.peek();
            if (event instanceof Event.BlockTag) {
                break;
            }
            parser.skip();
            var n = switch (event) {
                case Event.InlineTag(var s) -> new InlineTagImpl(s, readInlineBody());
                case JavadocTree e -> e;
                default -> null;
            };
            if (n != null) {
                if (body == null) {
                    body = new ArrayList<>();
                }
                body.add(n);
            }
        }
        return body == null ? List.of() : Collections.unmodifiableList(body);
    }

    private String readInlineBody() {
        String body = "";
        while (parser.hasNext()) {
            var event = parser.peek();
            if (event instanceof Text text) {
                body = text.value();
                parser.skip();
            } else {
                return body;
            }
        }
        throw new IllegalStateException("Unexpected EOF");
    }

    private Map<String, AttrValue> readAttributes() {
        Map<String, AttrValue> attrs = null;
        String key = null;
        while (parser.hasNext()) {
            var event = parser.peek();
            switch (event) {
                case Event.AttrName(var name) -> {
                    parser.skip();
                    if (attrs == null) {
                        attrs = new LinkedHashMap<>();
                    }
                    attrs.put(name, AttrValue.EMPTY);
                    key = name;
                }
                case AttrValue v -> {
                    parser.skip();
                    if (attrs == null) {
                        attrs = new LinkedHashMap<>();
                    }
                    attrs.put(key, v);
                }
                default -> {
                    return attrs == null ? Map.of() : Collections.unmodifiableMap(attrs);
                }
            }
        }
        throw new IllegalStateException("Unexpected EOF");
    }

    private static boolean isSentenceBreak(String tag) {
        return switch (tag.toUpperCase()) {
            case "H1", "H2", "H3", "H4", "H5", "H6", "PRE", "P" -> true;
            default -> false;
        };
    }

    private static int sentenceBreak(String s) {
        for (int i = 0, period = -1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                period = i;
            } else if (Character.isWhitespace(c)) {
                if (period >= 0) {
                    return i;
                }
            } else {
                period = -1;
            }
        }
        return -1;
    }

    private int skipWhiteSpace(String s, int offset) {
        for (int i = offset; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }

    record EltStartImpl(String name, boolean selfClosing, Map<String, JavadocTree.AttrValue> attributes)
            implements JavadocTree.EltStart {

        EltStartImpl {
            attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(attributes);
        }
    }

    record DocumentImpl(List<JavadocTree> firstSentence, List<JavadocTree> body, List<BlockTag> blockTags)
            implements JavadocTree.Document {
    }

    record EltCloseImpl(String name) implements JavadocTree.EltClose {
    }

    record TextImpl(String value) implements JavadocTree.Text {
    }

    record EscapeImpl(String value) implements JavadocTree.Escape {
    }

    record CommentImpl(String value) implements JavadocTree.Comment {
    }

    record CdataImpl(String value) implements JavadocTree.Cdata {
    }

    record DoctypeImpl(String value) implements JavadocTree.Doctype {
    }

    record InlineTagImpl(String tag, String body) implements JavadocTree.InlineTag {
    }

    record BlockTagImpl(String tag, List<JavadocTree> body) implements JavadocTree.BlockTag {
    }
}
