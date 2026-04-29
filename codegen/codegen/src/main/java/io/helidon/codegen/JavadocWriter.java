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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.helidon.codegen.JavadocTree.AttrValue;
import io.helidon.codegen.JavadocTree.AttrValue.Kind;
import io.helidon.codegen.JavadocTree.EltClose;
import io.helidon.codegen.JavadocTree.EltStart;
import io.helidon.codegen.JavadocTree.Escape;
import io.helidon.codegen.JavadocTree.InlineTag;
import io.helidon.codegen.JavadocTree.Text;
import io.helidon.common.Api;

/**
 * A tool that renders Javadoc to simplified HTML.
 * <p>
 * The following inline tags are rendered, all other inline tags are ignored:
 * <ul>
 *     <li>{@code summary}: rendered as <code>&lt;summary&gt;</code></li>
 *     <li>{@code value}: rendered as <code>&lt;code&gt;</code></li>
 *     <li>{@code code}: rendered as <code>&lt;code&gt;</code></li>
 *     <li>{@code literal}: rendered as <code>&lt;code&gt;</code></li>
 *     <li>{@code link}: rendered as <code>&lt;code&gt;</code></li>
 *     <li>{@code linkplain}: rendered as <code>&lt;code&gt;</code></li>
 * </ul>
 * <p>
 * Block tags ignored.
 * </p>
 * <p>
 * HTML elements are re-rendered from the parsed tree.
 * <p>
 * <b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
@Api.Internal
public final class JavadocWriter {

    private final StringBuilder buf;
    private final Deque<String> stack = new ArrayDeque<>();

    private JavadocWriter(StringBuilder buf) {
        this.buf = buf;
    }

    /**
     * Create a new instance.
     *
     * @param buf buf
     * @return writer
     */
    public static JavadocWriter create(StringBuilder buf) {
        return new JavadocWriter(buf);
    }

    @Override
    public String toString() {
        return buf.toString();
    }

    /**
     * Write the given elements.
     *
     * @param elements elements
     */
    public void write(List<? extends JavadocTree> elements) {
        for (var e : elements) {
            write(e);
        }
        closeElements();
    }

    /**
     * Write the given element.
     *
     * @param element element
     */
    public void write(JavadocTree element) {
        switch (element) {
            case EltStart elt -> writeStartElement(elt.name(), elt.selfClosing(), elt.attributes());
            case EltClose close -> writeEndElement(close.name());
            case Text text -> buf.append(encode(buf.isEmpty() ? text.value().stripLeading() : text.value()));
            case Escape escape -> buf.append(encode(escape.value()));
            case InlineTag tag -> {
                var tagName = tag.tag();
                var body = tag.body();
                switch (tagName) {
                    case "link", "linkplain", "value", "code" -> {
                        writeStartElement("code", false, Map.of());
                        buf.append(encode(body));
                        writeEndElement("code");
                    }
                    case "literal" -> buf.append(encode(body));
                    case "summary" -> {
                        writeStartElement("summary", false, Map.of());
                        buf.append(encode(body));
                        writeEndElement("summary");
                    }
                    default -> {
                        // ignore
                    }
                }
            }
            default -> {
                // ignore
            }
        }
    }

    private void writeStartElement(String tag, boolean selfClosing, Map<String, AttrValue> attrs) {
        closeElements(tag);
        buf.append("<");
        buf.append(tag);
        attrs.forEach(this::writeAttribute);
        if (selfClosing) {
            buf.append("/");
        }
        buf.append(">");
        if (!selfClosing && !isVoidElement(tag)) {
            stack.push(tag);
        }
    }

    private void writeAttribute(String name, AttrValue value) {
        buf.append(" ");
        buf.append(name);
        switch (value.kind()) {
            case Kind.SINGLE -> {
                buf.append("='");
                buf.append(value.value());
                buf.append("'");
            }
            case Kind.DOUBLE -> {
                buf.append("=\"");
                buf.append(value.value());
                buf.append("\"");
            }
            case Kind.UNQUOTED -> {
                buf.append("=");
                buf.append(value.value());
            }
            default -> {
                // do nothing
            }
        }
    }

    private void writeEndElement(String tag) {
        if (hasOpenElement(tag)) {
            while (!tag.equalsIgnoreCase(stack.peek())) {
                writeRawEndElement(stack.pop());
            }
            stack.pop();
            writeRawEndElement(tag);
        }
    }

    private void closeElements(String startTag) {
        while (!stack.isEmpty() && isClosedBy(stack.peek(), startTag)) {
            writeRawEndElement(stack.pop());
        }
    }

    private void closeElements() {
        while (!stack.isEmpty()) {
            writeRawEndElement(stack.pop());
        }
    }

    private boolean hasOpenElement(String tag) {
        for (var elt : stack) {
            if (elt.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    private void writeRawEndElement(String tag) {
        buf.append("</");
        buf.append(tag.toLowerCase());
        buf.append(">");
    }

    private static StringBuilder encode(String str) {
        var buf = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '&':
                    if (isEntity(str, i)) {
                        buf.append(c);
                    } else {
                        buf.append("&amp;");
                    }
                    break;
                case '>':
                    buf.append("&gt;");
                    break;
                case '<':
                    buf.append("&lt;");
                    break;
                default:
                    buf.append(c);
            }
        }
        return buf;
    }

    private static boolean isEntity(String str, int offset) {
        if (str.length() >= offset + 3) {
            int index = offset;
            char c = str.charAt(index++);
            if (c == '&') {
                c = str.charAt(index++);
                if (c == '#') {
                    c = str.charAt(index++);
                    if (Character.toLowerCase(c) == 'x') {
                        return isEntity(str, index, JavadocWriter::isValidHexChar);
                    }
                    return isEntity(str, index, Character::isDigit);
                }
                return isEntity(str, index, Character::isLetter);
            }
        }
        return false;
    }

    private static boolean isEntity(String str, int offset, Predicate<Character> predicate) {
        for (int i = offset; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == ';') {
                return true;
            } else if (!predicate.test(c)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isValidHexChar(char c) {
        return Character.digit(c, 16) != -1;
    }

    private static boolean isVoidElement(String elt) {
        return isElement(elt,
                "area", "base", "br", "col", "command",
                "embed", "hr", "img", "input", "keygen", "link", "meta",
                "param", "source", "track", "wbr");
    }

    private static boolean isClosedBy(String openTag, String startTag) {
        return switch (openTag) {
            case "li" -> isElement(startTag, "li");
            case "optgroup" -> isElement(startTag, "optgroup");
            case "tfoot" -> isElement(startTag, "tbody");
            case "colgroup" -> !isElement(startTag, "col");
            case "dt", "dd" -> isElement(startTag, "dt", "dd");
            case "p" -> isElement(startTag, "address", "article", "aside", "blockquote", "dir", "div", "dl",
                    "fieldset", "footer", "form", "h1", "h2", "h3", "h4", "h5",
                    "h6", "header", "hgroup", "hr", "menu", "nav", "ol", "p",
                    "pre", "section", "table", "ul");
            case "rt", "rp" -> isElement(startTag, "rt", "rp");
            case "option" -> isElement(startTag, "option", "optgroup");
            case "thead", "tbody" -> isElement(startTag, "tbody", "tfoot");
            case "tr" -> isElement(startTag, "tr", "tbody", "tfoot");
            case "td", "th" -> isElement(startTag, "td", "th", "tr", "tobdy", "tfoot");
            default -> false;
        };
    }

    private static boolean isElement(String elt, String... elements) {
        for (var e : elements) {
            if (e.equalsIgnoreCase(elt)) {
                return true;
            }
        }
        return false;
    }
}
