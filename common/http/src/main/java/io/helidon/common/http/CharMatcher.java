/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;

/**
 * Extracted from Guava.
 * <p>
 * Determines a true or false value for any Java {@code char} value, just as {@link java.util.function.Predicate} does
 * for any {@link Object}. Also offers basic text processing methods based on this function.
 * Implementations are strongly encouraged to be side-effect-free and immutable.
 *
 * <p>Throughout the documentation of this class, the phrase "matching character" is used to mean
 * "any {@code char} value {@code c} for which {@code this.matches(c)} returns {@code true}".
 *
 * <p><b>Warning:</b> This class deals only with {@code char} values; it does not understand
 * supplementary Unicode code points in the range {@code 0x10000} to {@code 0x10FFFF}. Such logical
 * characters are encoded into a {@code String} using surrogate pairs, and a {@code CharMatcher}
 * treats these just as two separate characters.
 *
 * <p>See the Guava User Guide article on <a
 * href="https://github.com/google/guava/wiki/StringsExplained#charmatcher">{@code CharMatcher}
 * </a>.
 *
 * @author Kevin Bourrillion
 */
@SuppressWarnings({"checkstyle:VisibilityModifier", "checkstyle:RedundantModifier"})
public abstract class CharMatcher {

    /**
     * Constructor for use by subclasses. When subclassing, you may want to override
     * {@code toString()} to provide a useful description.
     */
    protected CharMatcher() {
    }

    /**
     * Determines whether a character is ASCII, meaning that its code point is less than 128.
     *
     * @return this CharMatcher instance
     */
    public static CharMatcher ascii() {
        return Ascii.INSTANCE;
    }

    /**
     * Returns a {@code char} matcher that matches any character except the one
     * specified.
     *
     * <p>
     * To negate another {@code CharMatcher}, use {@link #negate()}.
     *
     * @param match the character that should not match
     * @return CharMatcher
     */
    public static CharMatcher isNot(final char match) {
        return new IsNot(match);
    }

    /**
     * Matches any character.
     *
     * @return CharMatcher
     */
    public static CharMatcher any() {
        return Any.INSTANCE;
    }

    /**
     * Matches no characters.
     *
     * @return CharMatcher
     */
    public static CharMatcher none() {
        return None.INSTANCE;
    }

    /**
     * Determines whether a character is an ISO control character as specified by
     * {@link Character#isISOControl(char)}.
     *
     * @return CharMatcher
     */
    public static CharMatcher javaIsoControl() {
        return JavaIsoControl.INSTANCE;
    }

    /**
     * Returns a {@code char} matcher that matches only one specified character.
     * @param match the character that should match
     * @return CharMatcher
     */
    public static CharMatcher is(final char match) {
        return new Is(match);
    }

    private static CharMatcher.IsEither isEither(char c1, char c2) {
        return new CharMatcher.IsEither(c1, c2);
    }

    /**
     * Returns a {@code char} matcher that matches any character not present in the given character
     * sequence.
     * @param sequence all the characters that should not be matched
     * @return CharMatcher
     */
    public static CharMatcher noneOf(CharSequence sequence) {
        return anyOf(sequence).negate();
    }

    /**
     * Returns a {@code char} matcher that matches any character present in the given character
     * sequence.
     * @param sequence all the characters that should be matched
     * @return CharMatcher
     */
    public static CharMatcher anyOf(final CharSequence sequence) {
        switch (sequence.length()) {
        case 0:
            return none();
        case 1:
            return is(sequence.charAt(0));
        case 2:
            return isEither(sequence.charAt(0), sequence.charAt(1));
        default:
            // TODO(lowasser): is it potentially worth just going ahead and building a precomputed
            // matcher?
            return new AnyOf(sequence);
        }
    }

    /**
     * Returns the Java Unicode escape sequence for the given character, in the form "\u12AB" where
     * "12AB" is the four hexadecimal digits representing the 16 bits of the UTF-16 character.
     */
    private static String showCharacter(char c) {
        String hex = "0123456789ABCDEF";
        char[] tmp = {'\\', 'u', '\0', '\0', '\0', '\0'};
        for (int i = 0; i < 4; i++) {
            tmp[5 - i] = hex.charAt(c & 0xF);
            c = (char) (c >> 4);
        }
        return String.copyValueOf(tmp);
    }

    /**
     * Determines a true or false value for the given character.
     *
     * @param c the character to match
     * @return {@code true} if this {@code CharMatcher} instance matches the
     * given character, {@code false} otherwise
     */
    public abstract boolean matches(char c);

    /**
     * Returns a matcher that matches any character not matched by this matcher.
     *
     * @return new {@code CharMatcher} instance representing the logical
     * negation of this instance
     */
    public CharMatcher negate() {
        return new Negated(this);
    }

    /**
     * Returns a matcher that matches any character matched by both this matcher and {@code other}.
     * @param other the other instance
     * @return new {@code CharMatcher} instance representing the logical
     * and of this instance and the {@code other} instance
     */
    public CharMatcher and(CharMatcher other) {
        return new And(this, other);
    }

    /**
     * Returns a matcher that matches any character matched by either this matcher or {@code other}.
     * @param other the other instance
     * @return new {@code CharMatcher} instance representing the logical
     * and of this instance and the {@code other} instance
     */
    public CharMatcher or(CharMatcher other) {
        return new Or(this, other);
    }

    // Abstract methods

    /**
     * Sets bits in {@code table} matched by this matcher.
     */
    void setBits(BitSet table) {
        for (int c = Character.MAX_VALUE; c >= Character.MIN_VALUE; c--) {
            if (matches((char) c)) {
                table.set(c);
            }
        }
    }

    // Non-static factories

    /**
     * Returns {@code true} if a character sequence contains at least one matching character.
     * Equivalent to {@code !matchesNoneOf(sequence)}.
     *
     * <p>The default implementation iterates over the sequence, invoking {@link #matches} for each
     * character, until this returns {@code true} or the end is reached.
     *
     * @param sequence the character sequence to examine, possibly empty
     * @return {@code true} if this matcher matches at least one character in the sequence
     * @since 8.0
     */
    public boolean matchesAnyOf(CharSequence sequence) {
        return !matchesNoneOf(sequence);
    }

    /**
     * Returns {@code true} if a character sequence contains only matching characters.
     *
     * <p>The default implementation iterates over the sequence, invoking {@link #matches} for each
     * character, until this returns {@code false} or the end is reached.
     *
     * @param sequence the character sequence to examine, possibly empty
     * @return {@code true} if this matcher matches every character in the sequence, including when
     * the sequence is empty
     */
    public boolean matchesAllOf(CharSequence sequence) {
        for (int i = sequence.length() - 1; i >= 0; i--) {
            if (!matches(sequence.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if a character sequence contains no matching characters. Equivalent to
     * {@code !matchesAnyOf(sequence)}.
     *
     * <p>The default implementation iterates over the sequence, invoking {@link #matches} for each
     * character, until this returns {@code true} or the end is reached.
     *
     * @param sequence the character sequence to examine, possibly empty
     * @return {@code true} if this matcher matches no characters in the sequence, including when
     * the sequence is empty
     */
    public boolean matchesNoneOf(CharSequence sequence) {
        return indexIn(sequence) == -1;
    }

    /**
     * Returns the index of the first matching character in a character sequence, or {@code -1} if no
     * matching character is present.
     *
     * <p>The default implementation iterates over the sequence in forward order calling
     * {@link #matches} for each character.
     *
     * @param sequence the character sequence to examine from the beginning
     * @return an index, or {@code -1} if no character matches
     */
    public int indexIn(CharSequence sequence) {
        return indexIn(sequence, 0);
    }

    /**
     * Returns the index of the first matching character in a character sequence, starting from a
     * given position, or {@code -1} if no character matches after that position.
     *
     * <p>The default implementation iterates over the sequence in forward order, beginning at {@code
     * start}, calling {@link #matches} for each character.
     *
     * @param sequence the character sequence to examine
     * @param start    the first index to examine; must be nonnegative and no greater than {@code
     *                 sequence.length()}
     * @return the index of the first matching character, guaranteed to be no less than {@code start},
     * or {@code -1} if no character matches
     * @throws IndexOutOfBoundsException if start is negative or greater than {@code
     *                                   sequence.length()}
     */
    public int indexIn(CharSequence sequence, int start) {
        int length = sequence.length();
        Preconditions.checkPositionIndex(start, length);
        for (int i = start; i < length; i++) {
            if (matches(sequence.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the number of matching characters found in a character sequence.
     * @param sequence sequence to count the number of matching characters
     * @return count of matching characters
     */
    public int countIn(CharSequence sequence) {
        int count = 0;
        for (int i = 0; i < sequence.length(); i++) {
            if (matches(sequence.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Implementation of {@link #ascii()}.
     */
    private static final class Ascii extends NamedFastMatcher {

        static final Ascii INSTANCE = new Ascii();

        Ascii() {
            super("CharMatcher.ascii()");
        }

        @Override
        public boolean matches(char c) {
            return c <= '\u007f';
        }
    }

    /**
     * {@link FastMatcher} which overrides {@code toString()} with a custom name.
     */
    abstract static class NamedFastMatcher extends FastMatcher {

        private final String description;

        NamedFastMatcher(String description) {
            this.description = Objects.requireNonNull(description);
        }

        @Override
        public final String toString() {
            return description;
        }
    }

    /**
     * A matcher for which pre-computation will not yield any significant benefit.
     */
    abstract static class FastMatcher extends CharMatcher {

        @Override
        public CharMatcher negate() {
            return new NegatedFastMatcher(this);
        }
    }

    /**
     * Negation of a {@link FastMatcher}.
     */
    static class NegatedFastMatcher extends Negated {

        NegatedFastMatcher(CharMatcher original) {
            super(original);
        }
    }

    /**
     * Implementation of {@link #javaIsoControl()}.
     */
    private static final class JavaIsoControl extends NamedFastMatcher {

        static final JavaIsoControl INSTANCE = new JavaIsoControl();

        private JavaIsoControl() {
            super("CharMatcher.javaIsoControl()");
        }

        @Override
        public boolean matches(char c) {
            return c <= '\u001f' || (c >= '\u007f' && c <= '\u009f');
        }
    }

    // Text processing routines

    /**
     * Implementation of {@link #negate()}.
     */
    private static class Negated extends CharMatcher {

        final CharMatcher original;

        Negated(CharMatcher original) {
            this.original = Objects.requireNonNull(original);
        }

        @Override
        public boolean matches(char c) {
            return !original.matches(c);
        }

        @Override
        public boolean matchesAllOf(CharSequence sequence) {
            return original.matchesNoneOf(sequence);
        }

        @Override
        public boolean matchesNoneOf(CharSequence sequence) {
            return original.matchesAllOf(sequence);
        }

        @Override
        public int countIn(CharSequence sequence) {
            return sequence.length() - original.countIn(sequence);
        }

        @Override
        void setBits(BitSet table) {
            BitSet tmp = new BitSet();
            original.setBits(tmp);
            tmp.flip(Character.MIN_VALUE, Character.MAX_VALUE + 1);
            table.or(tmp);
        }

        @Override
        public CharMatcher negate() {
            return original;
        }

        @Override
        public String toString() {
            return original + ".negate()";
        }
    }

    /**
     * Implementation of {@link #and(CharMatcher)}.
     */
    private static final class And extends CharMatcher {

        final CharMatcher first;
        final CharMatcher second;

        And(CharMatcher a, CharMatcher b) {
            first = Objects.requireNonNull(a);
            second = Objects.requireNonNull(b);
        }

        @Override
        public boolean matches(char c) {
            return first.matches(c) && second.matches(c);
        }

        @Override
        void setBits(BitSet table) {
            BitSet tmp1 = new BitSet();
            first.setBits(tmp1);
            BitSet tmp2 = new BitSet();
            second.setBits(tmp2);
            tmp1.and(tmp2);
            table.or(tmp1);
        }

        @Override
        public String toString() {
            return "CharMatcher.and(" + first + ", " + second + ")";
        }
    }

    /**
     * Implementation of {@link #or(CharMatcher)}.
     */
    private static final class Or extends CharMatcher {

        final CharMatcher first;
        final CharMatcher second;

        Or(CharMatcher a, CharMatcher b) {
            first = Objects.requireNonNull(a);
            second = Objects.requireNonNull(b);
        }

        @Override
        void setBits(BitSet table) {
            first.setBits(table);
            second.setBits(table);
        }

        @Override
        public boolean matches(char c) {
            return first.matches(c) || second.matches(c);
        }

        @Override
        public String toString() {
            return "CharMatcher.or(" + first + ", " + second + ")";
        }
    }

    /**
     * Implementation of {@link #isNot(char)}.
     */
    private static final class IsNot extends FastMatcher {

        private final char match;

        IsNot(char match) {
            this.match = match;
        }

        @Override
        public boolean matches(char c) {
            return c != match;
        }

        @Override
        public CharMatcher and(CharMatcher other) {
            return other.matches(match) ? super.and(other) : other;
        }

        @Override
        public CharMatcher or(CharMatcher other) {
            return other.matches(match) ? any() : this;
        }

        @Override
        void setBits(BitSet table) {
            table.set(0, match);
            table.set(match + 1, Character.MAX_VALUE + 1);
        }

        @Override
        public CharMatcher negate() {
            return is(match);
        }

        @Override
        public String toString() {
            return "CharMatcher.isNot('" + showCharacter(match) + "')";
        }
    }

    /**
     * Implementation of {@link #anyOf(CharSequence)} for three or more characters.
     */
    private static final class AnyOf extends CharMatcher {

        private final char[] chars;

        public AnyOf(CharSequence chars) {
            this.chars = chars.toString().toCharArray();
            Arrays.sort(this.chars);
        }

        @Override
        public boolean matches(char c) {
            return Arrays.binarySearch(chars, c) >= 0;
        }

        @Override
        void setBits(BitSet table) {
            for (char c : chars) {
                table.set(c);
            }
        }

        @Override
        public String toString() {
            StringBuilder description = new StringBuilder("CharMatcher.anyOf(\"");
            for (char c : chars) {
                description.append(showCharacter(c));
            }
            description.append("\")");
            return description.toString();
        }
    }

    /**
     * Implementation of {@link #is(char)}.
     */
    private static final class Is extends FastMatcher {

        private final char match;

        Is(char match) {
            this.match = match;
        }

        @Override
        public boolean matches(char c) {
            return c == match;
        }

        @Override
        public CharMatcher and(CharMatcher other) {
            return other.matches(match) ? this : none();
        }

        @Override
        public CharMatcher or(CharMatcher other) {
            return other.matches(match) ? other : super.or(other);
        }

        @Override
        public CharMatcher negate() {
            return isNot(match);
        }

        @Override
        void setBits(BitSet table) {
            table.set(match);
        }

        @Override
        public String toString() {
            return "CharMatcher.is('" + showCharacter(match) + "')";
        }
    }

    /**
     * Implementation of {@link #any()}.
     */
    private static final class Any extends NamedFastMatcher {

        static final Any INSTANCE = new Any();

        private Any() {
            super("CharMatcher.any()");
        }

        @Override
        public boolean matches(char c) {
            return true;
        }

        @Override
        public int indexIn(CharSequence sequence) {
            return (sequence.length() == 0) ? -1 : 0;
        }

        @Override
        public int indexIn(CharSequence sequence, int start) {
            int length = sequence.length();
            Preconditions.checkPositionIndex(start, length);
            return (start == length) ? -1 : start;
        }

        @Override
        public boolean matchesAllOf(CharSequence sequence) {
            Objects.requireNonNull(sequence);
            return true;
        }

        @Override
        public boolean matchesNoneOf(CharSequence sequence) {
            return sequence.length() == 0;
        }

        @Override
        public int countIn(CharSequence sequence) {
            return sequence.length();
        }

        @Override
        public CharMatcher and(CharMatcher other) {
            return Objects.requireNonNull(other);
        }

        @Override
        public CharMatcher or(CharMatcher other) {
            Objects.requireNonNull(other);
            return this;
        }

        @Override
        public CharMatcher negate() {
            return none();
        }
    }

    /**
     * Implementation of {@link #none()}.
     */
    private static final class None extends NamedFastMatcher {

        static final None INSTANCE = new None();

        private None() {
            super("CharMatcher.none()");
        }

        @Override
        public boolean matches(char c) {
            return false;
        }

        @Override
        public int indexIn(CharSequence sequence) {
            Objects.requireNonNull(sequence);
            return -1;
        }

        @Override
        public int indexIn(CharSequence sequence, int start) {
            int length = sequence.length();
            Preconditions.checkPositionIndex(start, length);
            return -1;
        }

        @Override
        public boolean matchesAllOf(CharSequence sequence) {
            return sequence.length() == 0;
        }

        @Override
        public boolean matchesNoneOf(CharSequence sequence) {
            Objects.requireNonNull(sequence);
            return true;
        }

        @Override
        public int countIn(CharSequence sequence) {
            Objects.requireNonNull(sequence);
            return 0;
        }

        @Override
        public CharMatcher and(CharMatcher other) {
            Objects.requireNonNull(other);
            return this;
        }

        @Override
        public CharMatcher or(CharMatcher other) {
            return Objects.requireNonNull(other);
        }

        @Override
        public CharMatcher negate() {
            return any();
        }
    }

    /**
     * Implementation of {@link #anyOf(CharSequence)} for exactly two characters.
     */
    private static final class IsEither extends FastMatcher {

        private final char match1;
        private final char match2;

        IsEither(char match1, char match2) {
            this.match1 = match1;
            this.match2 = match2;
        }

        @Override
        public boolean matches(char c) {
            return c == match1 || c == match2;
        }

        @Override
        void setBits(BitSet table) {
            table.set(match1);
            table.set(match2);
        }

        @Override
        public String toString() {
            return "CharMatcher.anyOf(\"" + showCharacter(match1) + showCharacter(match2) + "\")";
        }
    }
}
