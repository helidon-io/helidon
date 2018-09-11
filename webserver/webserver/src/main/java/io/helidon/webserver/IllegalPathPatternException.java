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

package io.helidon.webserver;

/**
 * Cannot parse path pattern.
 * Path pattern is a WebServer standard regular language for URI path based routing.
 */
public class IllegalPathPatternException extends IllegalArgumentException {

    private final String pattern;
    private final int index;

    /**
     * Creates new instance.
     *
     * @param message Description of the problem.
     * @param pattern Illegal used pattern.
     * @param index index of the problematic character.
     */
    public IllegalPathPatternException(String message, String pattern, int index) {
        super(message + " (at " + index + " in pattern: " + pattern + ")");
        this.pattern = pattern;
        this.index = index;
    }

    public String getPattern() {
        return pattern;
    }

    public int getIndex() {
        return index;
    }
}
