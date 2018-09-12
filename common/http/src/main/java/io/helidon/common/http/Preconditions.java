/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Copied from Guava.
 * <p>
 * Static convenience methods that help a method or constructor check whether it was invoked
 * correctly (whether its <i>preconditions</i> have been met). These methods generally accept a
 * {@code boolean} expression which is expected to be {@code true} (or in the case of {@code
 * checkNotNull}, an object reference which is expected to be non-null). When {@code false} (or
 * {@code null}) is passed instead, the {@code Preconditions} method throws an unchecked exception,
 * which helps the calling method communicate to <i>its</i> caller that <i>that</i> caller has made
 * a mistake. Example: <pre>   {@code
 *
 *   /**
 *    * Returns the positive square root of the given value.
 *    *
 *    * @throws IllegalArgumentException if the value is negative
 *    *}{@code /
 *   public static double sqrt(double value) {
 *     Preconditions.checkArgument(value >= 0.0, "negative value: %s", value);
 *     // calculate the square root
 *   }
 *
 *   void exampleBadCaller() {
 *     double d = sqrt(-1.0);
 *   }}</pre>
 *
 * In this example, {@code checkArgument} throws an {@code IllegalArgumentException} to indicate
 * that {@code exampleBadCaller} made an error in <i>its</i> call to {@code sqrt}.
 *
 * <h3>Warning about performance</h3>
 *
 * <p>The goal of this class is to improve readability of code, but in some circumstances this may
 * come at a significant performance cost. Remember that parameter values for message construction
 * must all be computed eagerly, and autoboxing and varargs array creation may happen as well, even
 * when the precondition check then succeeds (as it should almost always do in production). In some
 * circumstances these wasted CPU cycles and allocations can add up to a real problem.
 * Performance-sensitive precondition checks can always be converted to the customary form:
 * <pre>   {@code
 *
 *   if (value < 0.0) {
 *     throw new IllegalArgumentException("negative value: " + value);
 *   }}</pre>
 *
 * <h3>Other types of preconditions</h3>
 *
 * <p>Not every type of precondition failure is supported by these methods. Continue to throw
 * standard JDK exceptions such as {@link java.util.NoSuchElementException} or
 * {@link UnsupportedOperationException} in the situations they are intended for.
 *
 * <h3>Non-preconditions</h3>
 *
 * <p>It is of course possible to use the methods of this class to check for invalid conditions
 * which are <i>not the caller's fault</i>. Doing so is <b>not recommended</b> because it is
 * misleading to future readers of the code and of stack traces. See
 * <a href="https://github.com/google/guava/wiki/ConditionalFailuresExplained">Conditional failures
 * explained</a> in the Guava User Guide for more advice.
 *
 * <h3>Only {@code %s} is supported</h3>
 *
 * <p>In {@code Preconditions} error message template strings, only the {@code "%s"} specifier is
 * supported, not the full range of {@link java.util.Formatter} specifiers.
 *
 * <h3>More information</h3>
 *
 * <p>See the Guava User Guide on
 * <a href="https://github.com/google/guava/wiki/PreconditionsExplained">using {@code
 * Preconditions}</a>.
 *
 * @author Kevin Bourrillion
 */
final class Preconditions {

    private Preconditions() {
    }

    /**
     * Ensures that {@code index} specifies a valid <i>position</i> in an array, list or string of
     * size {@code size}. A position index may range from zero to {@code size}, inclusive.
     *
     * @param index a user-supplied index identifying a position in an array, list or string
     * @param size  the size of that array, list or string
     * @return the value of {@code index}
     * @throws IndexOutOfBoundsException if {@code index} is negative or is greater than {@code size}
     * @throws IllegalArgumentException  if {@code size} is negative
     */
    public static int checkPositionIndex(int index, int size) {
        return checkPositionIndex(index, size, "index");
    }

    /**
     * Ensures that {@code index} specifies a valid <i>position</i> in an array, list or string of
     * size {@code size}. A position index may range from zero to {@code size}, inclusive.
     *
     * @param index a user-supplied index identifying a position in an array, list or string
     * @param size  the size of that array, list or string
     * @param desc  the text to use to describe this index in an error message
     * @return the value of {@code index}
     * @throws IndexOutOfBoundsException if {@code index} is negative or is greater than {@code size}
     * @throws IllegalArgumentException  if {@code size} is negative
     */
    public static int checkPositionIndex(int index, int size, String desc) {
        // Carefully optimized for execution by hotspot (explanatory comment above)
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException(badPositionIndex(index, size, desc));
        }
        return index;
    }

    /**
     * Ensures that {@code start} and {@code end} specify a valid <i>positions</i> in an array, list
     * or string of size {@code size}, and are in order. A position index may range from zero to
     * {@code size}, inclusive.
     *
     * @param start a user-supplied index identifying a starting position in an array, list or string
     * @param end   a user-supplied index identifying a ending position in an array, list or string
     * @param size  the size of that array, list or string
     * @throws IndexOutOfBoundsException if either index is negative or is greater than {@code size},
     *                                   or if {@code end} is less than {@code start}
     * @throws IllegalArgumentException  if {@code size} is negative
     */
    public static void checkPositionIndexes(int start, int end, int size) {
        // Carefully optimized for execution by hotspot (explanatory comment above)
        if (start < 0 || end < start || end > size) {
            throw new IndexOutOfBoundsException(badPositionIndexes(start, end, size));
        }
    }

    private static String badPositionIndexes(int start, int end, int size) {
        if (start < 0 || start > size) {
            return badPositionIndex(start, size, "start index");
        }
        if (end < 0 || end > size) {
            return badPositionIndex(end, size, "end index");
        }
        // end < start
        return format("end index (%s) must not be less than start index (%s)", end, start);
    }

    private static String badPositionIndex(int index, int size, String desc) {
        if (index < 0) {
            return format("%s (%s) must not be negative", desc, index);
        } else if (size < 0) {
            throw new IllegalArgumentException("negative size: " + size);
        } else { // index > size
            return format("%s (%s) must not be greater than size (%s)", desc, index, size);
        }
    }

    /**
     * Substitutes each {@code %s} in {@code template} with an argument. These are matched by
     * position: the first {@code %s} gets {@code args[0]}, etc. If there are more arguments than
     * placeholders, the unmatched arguments will be appended to the end of the formatted message in
     * square braces.
     *
     * @param template a non-null string containing 0 or more {@code %s} placeholders.
     * @param args     the arguments to be substituted into the message template. Arguments are converted
     *                 to strings using {@link String#valueOf(Object)}. Arguments can be null.
     */
    // Note that this is somewhat-improperly used from Verify.java as well.
    static String format(String template, Object... args) {
        template = String.valueOf(template); // null -> "null"

        // start substituting the arguments into the '%s' placeholders
        StringBuilder builder = new StringBuilder(template.length() + 16 * args.length);
        int templateStart = 0;
        int i = 0;
        while (i < args.length) {
            int placeholderStart = template.indexOf("%s", templateStart);
            if (placeholderStart == -1) {
                break;
            }
            builder.append(template, templateStart, placeholderStart);
            builder.append(args[i++]);
            templateStart = placeholderStart + 2;
        }
        builder.append(template, templateStart, template.length());

        // if we run out of placeholders, append the extra args in square braces
        if (i < args.length) {
            builder.append(" [");
            builder.append(args[i++]);
            while (i < args.length) {
                builder.append(", ");
                builder.append(args[i++]);
            }
            builder.append(']');
        }

        return builder.toString();
    }
}
