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

package io.helidon.common.buffers;

/**
 * Extracted from Guava.
 * <p>
 * Static methods pertaining to ASCII characters (those in the range of values {@code 0x00} through
 * {@code 0x7F}), and to strings containing such characters.
 *
 * original author Craig Berry
 * original author Gregory Kick
 * original since 7.0
 */
public final class Ascii {

    private Ascii() {
    }

    /**
     * Returns a copy of the input string in which all {@linkplain #isUpperCase(char) uppercase ASCII
     * characters} have been converted to lowercase. All other characters are copied without
     * modification.
     *
     * @param string string to lower case
     * @return lower cased string
     */
    public static String toLowerCase(String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (isUpperCase(string.charAt(i))) {
                char[] chars = string.toCharArray();
                for (; i < length; i++) {
                    char c = chars[i];
                    if (isUpperCase(c)) {
                        chars[i] = (char) (c ^ 0x20);
                    }
                }
                return String.valueOf(chars);
            }
        }
        return string;
    }

    /**
     * Returns a copy of the input character sequence in which all {@linkplain #isLowerCase(char)
     * lowercase ASCII characters} have been converted to uppercase. All other characters are copied
     * without modification.
     *
     * @param chars character sequence to upper case
     * @return uppercase case value
     * original since 14.0
     */
    public static String toUpperCase(CharSequence chars) {
        if (chars instanceof String string) {
            return toUpperCase(string);
        }
        char[] newChars = new char[chars.length()];
        for (int i = 0; i < newChars.length; i++) {
            newChars[i] = toUpperCase(chars.charAt(i));
        }
        return String.valueOf(newChars);
    }

    /**
     * Returns a copy of the input string in which all {@linkplain #isLowerCase(char) lowercase ASCII
     * characters} have been converted to uppercase. All other characters are copied without
     * modification.
     *
     * @param string string to upper case
     * @return upper cased string
     */
    public static String toUpperCase(String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (isLowerCase(string.charAt(i))) {
                char[] chars = string.toCharArray();
                for (; i < length; i++) {
                    char c = chars[i];
                    if (isLowerCase(c)) {
                        chars[i] = (char) (c ^ 0x20);
                    }
                }
                return String.valueOf(chars);
            }
        }
        return string;
    }

    /**
     * Returns a copy of the input character sequence in which all {@linkplain #isUpperCase(char)
     * uppercase ASCII characters} have been converted to lowercase. All other characters are copied
     * without modification.
     *
     * @param chars character sequence to lower case
     * @return lower case value
     * original since 14.0
     */
    public static String toLowerCase(CharSequence chars) {
        if (chars instanceof String) {
            return toLowerCase((String) chars);
        }
        char[] newChars = new char[chars.length()];
        for (int i = 0; i < newChars.length; i++) {
            newChars[i] = toLowerCase(chars.charAt(i));
        }
        return String.valueOf(newChars);
    }

    /**
     * If the argument is an {@linkplain #isUpperCase(char) uppercase ASCII character} returns the
     * lowercase equivalent. Otherwise returns the argument.
     *
     * @param c character
     * @return character as a lower case
     */
    public static char toLowerCase(char c) {
        return isUpperCase(c) ? (char) (c ^ 0x20) : c;
    }

    /**
     * Indicates whether {@code c} is one of the twenty-six lowercase ASCII alphabetic characters
     * between {@code 'a'} and {@code 'z'} inclusive. All others (including non-ASCII characters)
     * return {@code false}.
     *
     * @param c character to check
     * @return whether the character is lower case
     */
    public static boolean isLowerCase(char c) {
        // Note: This was benchmarked against the alternate expression "(char)(c - 'a') < 26" (Nov '13)
        // and found to perform at least as well, or better.
        return (c >= 'a') && (c <= 'z');
    }

    /**
     * If the argument is a {@linkplain #isLowerCase(char) lowercase ASCII character} returns the
     * uppercase equivalent. Otherwise returns the argument.
     * @param c character
     * @return character as a lower case
     */
    public static char toUpperCase(char c) {
        return isLowerCase(c) ? (char) (c ^ 0x20) : c;
    }

    /**
     * Indicates whether {@code c} is one of the twenty-six uppercase ASCII alphabetic characters
     * between {@code 'A'} and {@code 'Z'} inclusive. All others (including non-ASCII characters)
     * return {@code false}.
     *
     * @param c character to check
     * @return whether the character is upper case
     */
    public static boolean isUpperCase(char c) {
        return (c >= 'A') && (c <= 'Z');
    }

}
