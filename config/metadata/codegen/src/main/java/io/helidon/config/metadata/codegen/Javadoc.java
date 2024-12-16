/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.codegen;

import java.util.regex.Pattern;

import static io.helidon.codegen.CodegenUtil.capitalize;

/*
Possible improvements:
- @link - create a proper javadoc reference (i.e. always fully qualified reference), such as:
    {@link:io.helidon.common.Type#method(java.lang.String)}, so we can generate a nice reference for docs
- @value - if possible, find the actual value (string, int etc.) and add it as `thevalue`
- @see - create a proper javadoc reference (as for @link)
 */
final class Javadoc {
    private static final Pattern JAVADOC_CODE = Pattern.compile("\\{@code (.*?)}");
    private static final Pattern JAVADOC_LINK = Pattern.compile("\\{@link (.*?)}");
    private static final Pattern JAVADOC_LINKPLAIN = Pattern.compile("\\{@linkplain (.*?)}");
    private static final Pattern JAVADOC_VALUE = Pattern.compile("\\{@value (.*?)}");
    private static final Pattern JAVADOC_SEE = Pattern.compile("@see (.*?\n)");

    private Javadoc() {
    }

    /**
     * Parses a Javadoc comment (provided as a string) into text that can be used for display/docs of the configuration option.
     * <p>
     * The following steps are done:
     * <ul>
     *     <li>{@code @param} is stripped from the text</li>
     *     <li>Any {@code @code} section: the code tag is removed, and surrounded with {@code '}</li>
     *     <li>Any {@code @link} section: the link tag is removed</li>
     *     <li>Any {@code @linkplain} section: the linkplain tag is removed</li>
     *     <li>Any {@code @value} section: the value tag is removed, {code #} is replaced with {@code .}</li>
     *     <li>Any {@code @see} section: the see tag is removed, prefixed with {@code See},
     *                  {code #} is replaced with {@code .}</li>
     *     <li>{@code @return} is stripped from the text, and the first letter is capitalized</li>
     * </ul>
     *
     * @param docComment "raw" javadoc from the source code
     * @return description of the option
     */
    static String parse(String docComment) {
        if (docComment == null) {
            return "";
        }

        String javadoc = docComment;
        int index = javadoc.indexOf("@param");
        if (index > -1) {
            javadoc = docComment.substring(0, index);
        }
        // replace all {@code xxx} with 'xxx'
        javadoc = JAVADOC_CODE.matcher(javadoc).replaceAll(it -> javadocCode(it.group(1)));
        // replace all {@link ...} with just the link
        javadoc = JAVADOC_LINK.matcher(javadoc).replaceAll(it -> javadocLink(it.group(1)));
        // replace all {@link ...} with just the name
        javadoc = JAVADOC_LINKPLAIN.matcher(javadoc).replaceAll(it -> javadocLink(it.group(1)));
        // replace all {@value ...} with just the reference
        javadoc = JAVADOC_VALUE.matcher(javadoc).replaceAll(it -> javadocValue(it.group(1)));
        // replace all {@see ...} with just the reference
        javadoc = JAVADOC_SEE.matcher(javadoc).replaceAll(it -> javadocSee(it.group(1)));

        int count = 9;
        index = javadoc.indexOf(" @return");
        if (index == -1) {
            count = 8;
            index = javadoc.indexOf("@return");
        }
        if (index > -1) {
            javadoc = javadoc.substring(0, index) + capitalize(javadoc.substring(index + count).trim());
        }

        return javadoc.trim();
    }

    private static String javadocSee(String originalValue) {
        return "See " + javadocValue(originalValue);
    }

    private static String javadocCode(String originalValue) {
        return '`' + originalValue + '`';
    }

    private static String javadocLink(String originalValue) {
        return javadocValue(originalValue);
    }

    private static String javadocValue(String originalValue) {
        if (originalValue.startsWith("#")) {
            return originalValue.substring(1);
        }
        return originalValue.replace('#', '.');
    }
}
