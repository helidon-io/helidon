/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import io.helidon.common.parameters.Parameters;

import static io.helidon.common.uri.UriEncoding.decodeUri;

class UriPathParameters implements Parameters {
    private final Map<String, List<String>> pathParams;

    private UriPathParameters(Map<String, List<String>> pathParams) {
        this.pathParams = pathParams;
    }

    static Parameters create(String rawPath) {
        Map<String, List<String>> pathParams = new HashMap<>();

        // first split into segments
        String[] segments = rawPath.split("/");
        // now for each segment, find path parameters

        // /user;domain=abc;u=a/john;u=b
        // must result in path parameters domain=abc and u=b
        for (String segment : segments) {
            int semicolon = segment.indexOf(';');
            if (semicolon == -1) {
                continue;
            }

            // remaining is now just the path params
            // domain=abc;u=a
            String remaining = segment.substring(semicolon + 1);

            while (true) {
                String param;

                semicolon = remaining.indexOf(';');
                if (semicolon == -1) {
                    // this segment does not contain any path parameters
                    if (remaining.isEmpty()) {
                        break;
                    } else {
                        param = remaining;
                        remaining = "";
                    }
                } else {
                    param = remaining.substring(0, semicolon);
                    remaining = remaining.substring(semicolon + 1);
                }

                // param is the next parameters
                // remaining is the rest of the string or empty
                // param now contains the path parameter
                int eq = param.indexOf('=');
                if (eq == -1) {
                    pathParams.computeIfAbsent(decodeUri(param), it -> new ArrayList<>(1))
                            .add("");
                } else {
                    // now each value may be comma separated
                    String[] values = param.substring(eq + 1).split(",");
                    List<String> valueList = pathParams.computeIfAbsent(decodeUri(param.substring(0, eq)),
                                                                        it -> new ArrayList<>(1));
                    for (String value : values) {
                        valueList.add(decodeUri(value));
                    }
                }
            }
        }

        pathParams.replaceAll((name, values) -> List.copyOf(values));
        return new UriPathParameters(pathParams);
    }

    @Override
    public List<String> all(String name) throws NoSuchElementException {
        List<String> value = pathParams.get(name);
        if (value == null) {
            throw new NoSuchElementException("This path does not contain parameter named \"" + name + "\"");
        }
        return value;
    }

    @Override
    public String value(String name) throws NoSuchElementException {
        List<String> value = pathParams.get(name);
        if (value == null) {
            throw new NoSuchElementException("This path does not contain parameter named \"" + name + "\"");
        }
        return value.get(0);
    }

    @Override
    public boolean contains(String name) {
        return pathParams.containsKey(name);
    }

    @Override
    public boolean isEmpty() {
        return pathParams.isEmpty();
    }

    @Override
    public int size() {
        return pathParams.size();
    }

    @Override
    public Set<String> names() {
        return pathParams.keySet();
    }

    @Override
    public String component() {
        return "uri-path-parameters";
    }

    @Override
    public String toString() {
        return component() + ": " + pathParams;
    }
}
