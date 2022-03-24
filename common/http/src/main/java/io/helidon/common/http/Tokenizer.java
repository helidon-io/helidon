/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
package io.helidon.common.http;

import java.util.function.Supplier;

/**
 * String tokenizer for parsing headers.
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class Tokenizer {

    /**
     * The input string.
     */
    final String input;

    /**
     * The current position.
     */
    int position = 0;

    /**
     * Create a new instance.
     * @param input string to parse
     */
    public Tokenizer(String input) {
        this.input = input;
    }

    /**
     * Get the token represented by the specified matcher and advance the
     * position the to next character if matched.
     *
     * @param matcher matcher to use
     * @return token matched, or {@code null} if not matched
     * @throws IllegalStateException if {@link #hasMore() } returns
     * {@code false}
     */
    public String consumeTokenIfPresent(CharMatcher matcher) {
        checkState(hasMore(), "No more elements!");
        int startPosition = position;
        position = matcher.negate().indexIn(input, startPosition);
        return position == startPosition ? null
                : hasMore() ? input.substring(startPosition, position)
                : input.substring(startPosition);
    }

    /**
     * Get the token represented by the specified matcher and advance the
     * position the to next character.
     *
     * @param matcher matcher to use
     * @return IllegalStateException if {@link #hasMore() } returns
     * {@code false} or if the matcher wasn't matched.
     */
    public String consumeToken(CharMatcher matcher) {
        int startPosition = position;
        String token = consumeTokenIfPresent(matcher);
        checkState(position != startPosition, ()
                -> String.format("Position '%d' should not be '%d'!", position,
                        startPosition));
        return token;
    }

    /**
     * Get the one character at the current position and matches it with the
     * specified matcher, then update the position to the next character.
     *
     * @param matcher matcher to use
     * @return consumed character
     * @throws IllegalStateException if {@link #hasMore() } returns
     * {@code false} or if the specified matcher does not match the character at
     * the current position
     */
    public char consumeCharacter(CharMatcher matcher) {
        checkState(hasMore(), "No more elements!");
        char c = previewChar();
        checkState(matcher.matches(c), "Unexpected character matched: " + c);
        position++;
        return c;
    }

    /**
     * Get the one character at the current position and matches it with the
     * specified character and update the position to the next character.
     *
     * @param c character to match
     * @return matched character
     * @throws IllegalStateException if {@link #hasMore() } returns
     * {@code false} or if the specified character does not match the character
     * at the current position
     */
    public char consumeCharacter(char c) {
        checkState(hasMore(), "No more elements!");
        checkState(previewChar() == c, () -> "Unexpected character: " + c);
        position++;
        return c;
    }

    /**
     * Get the character at the current position.
     * @return char
     * @throws IllegalStateException if {@link #hasMore() } returns {@code false}
     */
    public char previewChar() {
        checkState(hasMore(), "No more elements!");
        return input.charAt(position);
    }

    /**
     * Test if there are more characters to process.
     * @return {@code true} if there are more characters to process, {@code false}
     * otherwise
     */
    public boolean hasMore() {
        return (position >= 0) && (position < input.length());
    }

    /**
     * Verify that the given token matches the specified matcher and return
     * a lower case only token string.
     * @param matcher matcher to use
     * @param token input token
     * @return normalized token string (lower case only)
     */
    public static String normalize(CharMatcher matcher, String token) {
        checkState(matcher.matchesAllOf(token), ()
                -> String.format(
                        "Parameter '%s' doesn't match token matcher: %s",
                        token, matcher));
        return Ascii.toLowerCase(token);
    }

    /**
     * Ensures the truth of an expression involving the state of the calling
     * instance, but not involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param message a message to pass to the {@link IllegalStateException}
     * that is possibly thrown
     * @throws IllegalStateException if {@code expression} is false
     */
    private static void checkState(boolean expression, String message) {
        checkState(expression, () -> message);
    }

    /**
     * Ensures the truth of an expression involving the state of the calling
     * instance, but not involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param messageSupplier a message to pass to the
     * {@link IllegalStateException} that is possibly thrown
     * @throws IllegalStateException if {@code expression} is false
     */
    private static void checkState(boolean expression,
            Supplier<String> messageSupplier) {
        if (!expression) {
            throw new IllegalStateException(messageSupplier.get());
        }
    }
}
