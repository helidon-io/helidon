/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.comments;

/**
 * Simple profanity detection utility class.
 */
class ProfanityDetector {
    private static final String[] BANNED_TERMS = new String[] {"spring", "nodejs", "vertx"};

    private ProfanityDetector() {
    }

    /**
     * Detects if a message contains one of the profane words.
     *
     * @param message a message to check.
     */
    static void detectProfanity(String message) {
        if (message == null) {
            return;
        }
        message = message.toLowerCase();
        for (String bannedTerm : BANNED_TERMS) {
            if (message.contains(bannedTerm)) {
                throw new ProfanityException(bannedTerm);
            }
        }
    }
}
