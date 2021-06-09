/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

class PathHelper {

    private PathHelper() {
    }

    /**
     * Canonicalizes a path by dropping a trailing '/'. Also if path is
     * empty then it becomes '/'.
     *
     * @param p path to canonicalize.
     * @return canonicalized path.
     */
    static String canonicalize(String p) {
        if (p == null || p.isEmpty() || p.equals("/")) {
            return "/";
        }
        int lastCharIndex = p.length() - 1;
        return p.charAt(lastCharIndex) == '/' ? p.substring(0, lastCharIndex) : p;
    }

    private enum State {
        NORMAL,
        PATH_PARAM,
        QUERY_PARAM
    }

    /**
     * Drops any path parameters for the purpose of matching against patterns.
     * The input to this method is assumed to be canonicalize but it may need
     * canonicalization after transformation.
     *
     * Examples: /admin;a=b/list;c=d;e=f -> /admin/list
     *
     * @param path original path.
     * @return path with path params removed.
     */
    static String extractPathParams(String path) {
        // Avoid conversion for the common case of no params
        if (!path.contains(";")) {
            return path;
        }

        // Skip over any param paths
        State state = State.NORMAL;
        int n = path.length();
        StringBuilder builder = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char ch = path.charAt(i);
            switch (ch) {
                case ';':
                    if (state == State.NORMAL) {
                        state = State.PATH_PARAM;
                    }
                    break;
                case '/':
                    if (state == State.QUERY_PARAM) {
                        throw new IllegalStateException("Unexpected state " + state);
                    }
                    state = State.NORMAL;
                    builder.append(ch);
                    break;
                case '?':
                    state = State.QUERY_PARAM;
                    builder.append(ch);
                    break;
                default:
                    if (state != State.PATH_PARAM) {
                        builder.append(ch);
                    }
                    break;
            }
        }
        return canonicalize(builder.toString());
    }
}
