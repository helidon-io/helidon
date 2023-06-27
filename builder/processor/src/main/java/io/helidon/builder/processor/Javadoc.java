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

package io.helidon.builder.processor;

import java.util.ArrayList;
import java.util.List;

record Javadoc(List<String> lines,
               List<Tag> parameters,
               List<String> returns,
               List<Tag> nonParamTags) {

    static Javadoc parse(List<String> documentation) {
        List<String> lines = documentation.stream()
                .map(String::trim)
                .toList();

        ParserState state = ParserState.LINES;
        List<String> result = new ArrayList<>();
        List<Tag> params = new ArrayList<>();
        List<String> returns = new ArrayList<>();
        List<Tag> otherTags = new ArrayList<>();

        String currentTagName = null;
        List<String> currentTag = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("@")) {
                // this is a new tag, finish previous, change state
                if (state == ParserState.PARAM) {
                    params.add(new Tag(currentTagName, List.copyOf(currentTag)));
                } else if (state == ParserState.TAG) {
                    otherTags.add(new Tag(currentTagName, List.copyOf(currentTag)));
                }
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
                } else if (line.startsWith("@return")) {
                    // return doc
                    state = ParserState.RETURNS;
                    returns.add(line.substring("@return".length()).trim()); // trim to remove whitespace after @returns
                } else {
                    // other tag
                    state = ParserState.TAG;
                    // @see some linke
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
                switch (state) {
                case LINES -> result.add(line);
                case RETURNS -> returns.add(line);
                default -> currentTag.add(line);
                }
            }
        }

        if (state == ParserState.PARAM) {
            if (currentTagName != null) {
                params.add(new Tag(currentTagName, List.copyOf(currentTag)));
            }
        } else {
            if (currentTagName != null) {
                otherTags.add(new Tag(currentTagName, List.copyOf(currentTag)));
            }
        }

        return new Javadoc(result,
                           params,
                           returns,
                           otherTags);
    }

    static Javadoc parse(String docString) {
        return parse(List.of(docString.split("\n")));
    }

    Javadoc removeFirstParam() {
        if (parameters.isEmpty()) {
            return this;
        }

        return new Javadoc(lines(),
                           parameters().subList(1, parameters().size()),
                           returns(),
                           nonParamTags());
    }

    Javadoc updateReturns(String returns) {
        return new Javadoc(lines(),
                           parameters(),
                           List.of(returns),
                           nonParamTags());

    }

    List<String> toLines() {
        List<String> result = new ArrayList<>(lines());

        for (Tag parameter : parameters) {
            addToTag(result, "@param " + parameter.name, parameter.tagLines());
        }

        if (!returns().isEmpty()) {
            addToTag(result, "@return", returns());
        }

        for (Tag tag : nonParamTags()) {
            addToTag(result, "@" + tag.name, tag.tagLines());
        }

        return result;
    }

    private void addToTag(List<String> result, String tag, List<String> tagLines) {
        if (tagLines.isEmpty()) {
            result.add(tag);
        } else {
            result.add(tag + " " + tagLines.get(0));
            result.addAll(tagLines.subList(1, tagLines.size()));
        }
    }

    private enum ParserState {
        LINES,
        PARAM,
        RETURNS,
        TAG
    }

    record Tag(String name, List<String> tagLines) {
    }
}
