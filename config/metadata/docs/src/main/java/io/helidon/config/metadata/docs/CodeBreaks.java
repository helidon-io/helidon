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

package io.helidon.config.metadata.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional break points for inline code in generated markdown.
 */
final class CodeBreaks {
    static final int MIN_CHARS_BETWEEN_BREAKS = 4;

    private static final String WBR = "<wbr>";
    private static final Pattern CODE = Pattern.compile("(?is)<code\\b([^>]*)>(.*?)</code>");

    private CodeBreaks() {
    }

    static String code(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        var result = new StringBuilder(text.length() + 32);
        appendCode(result, text);
        return result.toString();
    }

    static String html(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        var matcher = CODE.matcher(html);
        var result = new StringBuilder(html.length());
        while (matcher.find()) {
            var replacement = "<code" + matcher.group(1) + ">" + codeHtml(matcher.group(2)) + "</code>";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void appendCode(StringBuilder result, String text) {
        var code = new ArrayList<CodeChar>(text.length());
        appendCodeChars(code, text);
        appendCode(result, code);
    }

    private static String codeHtml(String html) {
        var code = new ArrayList<CodeChar>(html.length());
        appendCodeHtmlChars(code, html);
        var result = new StringBuilder(html.length() + 32);
        appendCode(result, code);
        return result.toString();
    }

    private static void appendCode(StringBuilder result, List<CodeChar> code) {
        var breaks = breakPositions(code);
        for (int index = 0; index < code.size(); index++) {
            if (breaks[index]) {
                result.append(WBR);
            }
            code.get(index).appendTo(result);
        }
    }

    private static boolean[] breakPositions(List<CodeChar> code) {
        var result = new boolean[code.size() + 1];
        var accepted = false;
        var previous = 0;
        for (int index = 0; index < code.size(); index++) {
            if (camelCaseBoundary(code, index)) {
                if (acceptBreak(result, index, accepted, previous)) {
                    accepted = true;
                    previous = index;
                }
            }
            if (separatorBoundary(code, index)) {
                var position = index + 1;
                if (acceptBreak(result, position, accepted, previous)) {
                    accepted = true;
                    previous = position;
                }
            }
        }
        return result;
    }

    private static boolean acceptBreak(boolean[] breaks, int position, boolean accepted, int previous) {
        if (!accepted || position - previous >= MIN_CHARS_BETWEEN_BREAKS) {
            breaks[position] = true;
            return true;
        }
        return false;
    }

    private static void appendCodeChars(List<CodeChar> code, String text) {
        for (int index = 0; index < text.length(); index++) {
            code.add(CodeChar.character(text.charAt(index)));
        }
    }

    private static void appendCodeHtmlChars(List<CodeChar> code, String html) {
        var chunkStart = 0;
        for (int index = 0; index < html.length(); index++) {
            if (html.charAt(index) != '&') {
                continue;
            }
            var semicolon = html.indexOf(';', index + 1);
            if (semicolon == -1) {
                continue;
            }
            var entity = html.substring(index + 1, semicolon);
            var decoded = decodeEntity(entity);
            if (decoded == null && !namedEntity(entity)) {
                continue;
            }
            appendCodeChars(code, html.substring(chunkStart, index));
            if (decoded == null) {
                code.add(CodeChar.rawHtml("&" + entity + ";"));
            } else {
                appendCodeChars(code, decoded);
            }
            index = semicolon;
            chunkStart = index + 1;
        }
        appendCodeChars(code, html.substring(chunkStart));
    }

    private static boolean camelCaseBoundary(List<CodeChar> code, int index) {
        if (index <= 0 || index >= code.size()) {
            return false;
        }

        var previous = code.get(index - 1);
        var current = code.get(index);
        if (!previous.letter() || !current.letter()) {
            return false;
        }
        if (previous.lowerCase() && current.upperCase()) {
            return true;
        }
        return previous.upperCase()
                && current.upperCase()
                && index + 1 < code.size()
                && code.get(index + 1).lowerCase();
    }

    private static boolean separatorBoundary(List<CodeChar> code, int index) {
        return index > 0
                && index + 1 < code.size()
                && code.get(index).separator();
    }

    private static boolean separator(char ch) {
        return switch (ch) {
            case '.', '/', '\\', ':', '?', '&', '=', '-', '_', '#', ',', '(', '<', '>' -> true;
            default -> false;
        };
    }

    private static void appendEscaped(StringBuilder result, char ch) {
        switch (ch) {
        case '&' -> result.append("&amp;");
        case '<' -> result.append("&lt;");
        case '>' -> result.append("&gt;");
        case '"' -> result.append("&quot;");
        case '\'' -> result.append("&#39;");
        default -> result.append(ch);
        }
    }

    private static String decodeEntity(String entity) {
        if (entity.isEmpty()) {
            return null;
        }
        if (entity.charAt(0) == '#') {
            return decodeNumericEntity(entity);
        }
        return switch (entity.toLowerCase(Locale.ROOT)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> "\u00a0";
            default -> null;
        };
    }

    private static boolean namedEntity(String entity) {
        if (entity.isEmpty() || entity.charAt(0) == '#') {
            return false;
        }
        if (!Character.isLetter(entity.charAt(0))) {
            return false;
        }
        return entity.chars().allMatch(Character::isLetterOrDigit);
    }

    private static String decodeNumericEntity(String entity) {
        try {
            int codePoint;
            if (entity.length() > 2 && (entity.charAt(1) == 'x' || entity.charAt(1) == 'X')) {
                codePoint = Integer.parseInt(entity.substring(2), 16);
            } else {
                codePoint = Integer.parseInt(entity.substring(1));
            }
            if (!Character.isValidCodePoint(codePoint)) {
                return null;
            }
            return Character.toString(codePoint);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record CodeChar(char ch, String rawHtml) {
        private static CodeChar character(char ch) {
            return new CodeChar(ch, null);
        }

        private static CodeChar rawHtml(String rawHtml) {
            return new CodeChar('\0', rawHtml);
        }

        private void appendTo(StringBuilder result) {
            if (rawHtml == null) {
                appendEscaped(result, ch);
            } else {
                result.append(rawHtml);
            }
        }

        private boolean letter() {
            return rawHtml == null && Character.isLetter(ch);
        }

        private boolean lowerCase() {
            return rawHtml == null && Character.isLowerCase(ch);
        }

        private boolean upperCase() {
            return rawHtml == null && Character.isUpperCase(ch);
        }

        private boolean separator() {
            return rawHtml == null && CodeBreaks.separator(ch);
        }
    }
}
