/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.common.processor.classmodel;

import java.util.ArrayList;
import java.util.List;

class JavadocParser {

    private JavadocParser() {
    }

    static Javadoc.Builder parse(Javadoc.Builder javadocBuilder, String docString) {
        return parse(javadocBuilder, List.of(docString.split("\n")));
    }

    static Javadoc.Builder parse(Javadoc.Builder javadocBuilder, List<String> documentation) {

        ParserState state = ParserState.LINES;

        String currentTagName = null;
        List<String> currentTag = new ArrayList<>();

        for (String raw : documentation) {
            String line = raw.trim();
            if (line.startsWith("@")) {
                // this is a new tag, finish previous, change state
                addTag(javadocBuilder, state, currentTagName, currentTag);
                currentTagName = null;
                currentTag.clear();
                // and now parse the current tag line
                if (line.startsWith("@param")) {
                    // param doc
                    state = ParserState.PARAM;
                    int space = line.indexOf(' ');
                    if (space < 0) {
                        // should be @param paramName documentation
                        // there is no param name defined, this is bad
                        // TODO add location!
                        throw new IllegalStateException("Failed to parse javadoc, @param without param name: " + line);
                    }
                    int secondSpace = line.indexOf(' ', space + 2);
                    if (secondSpace < 0) {
                        throw new IllegalStateException("Failed to parse javadoc, @param without param name or docs: " + line);
                    }
                    currentTagName = line.substring(space + 1, secondSpace);
                    currentTag.add(line.substring(secondSpace + 1));
                    if (currentTagName.startsWith("<")) {
                        currentTagName = currentTagName.substring(1, currentTagName.indexOf(">"));
                        state = ParserState.GENERIC_PARAM;
                    }
                } else if (line.startsWith("@return")) {
                    // return doc
                    state = ParserState.RETURNS;
                    currentTag.add(line.substring("@return".length()).trim()); // trim to remove whitespace after @returns
                } else if (line.startsWith("@throws")) {
                    // throw doc
                    state = ParserState.THROWS;
                    int space = line.indexOf(' ');
                    if (space < 0) {
                        // should be @throws exception documentation
                        // there is no exception name defined, this is bad
                        throw new IllegalStateException("Failed to parse javadoc, @throws without exception name: " + line);
                    }
                    int secondSpace = line.indexOf(' ', space + 2);
                    if (secondSpace < 0) {
                        throw new IllegalStateException("Failed to parse javadoc, @throws without exception name or docs: "
                                                                + line);
                    }
                    currentTagName = line.substring(space + 1, secondSpace);
                    currentTag.add(line.substring(secondSpace + 1));
                } else {
                    // other tag
                    state = ParserState.TAG;
                    // @see some link
                    int space = line.indexOf(' ');
                    if (space < 0) {
                        // should be @tag documentation
                        // TODO add location!
                        throw new IllegalStateException("Failed to parse javadoc, @tag without space: " + line);
                    }
                    currentTagName = line.substring(1, space); // without @
                    currentTag.add(line.substring(space + 1));
                }
            } else {
                // continuation of previous state
                if (state == ParserState.LINES) {
                    javadocBuilder.addLine(raw);
                } else {
                    currentTag.add(line);
                }
            }
        }

        addTag(javadocBuilder, state, currentTagName, currentTag);

        return javadocBuilder;
    }

    private static void addTag(Javadoc.Builder javadocBuilder,
                               ParserState state,
                               String currentTagName,
                               List<String> currentTag) {
        if (state == ParserState.PARAM) {
            javadocBuilder.addParameter(currentTagName, currentTag);
        } else if (state == ParserState.GENERIC_PARAM) {
            javadocBuilder.addGenericArgument(currentTagName, currentTag);
        } else if (state == ParserState.TAG) {
            if ("deprecated".equals(currentTagName)) {
                javadocBuilder.deprecation(currentTag);
            } else {
                javadocBuilder.addTag(currentTagName, currentTag);
            }
        } else if (state == ParserState.RETURNS) {
            javadocBuilder.returnDescription(currentTag);
        } else if (state == ParserState.THROWS) {
            javadocBuilder.addThrows(currentTagName, currentTag);
        }
    }

    private enum ParserState {
        LINES,
        PARAM,
        GENERIC_PARAM,
        THROWS,
        RETURNS,
        TAG
    }

}
