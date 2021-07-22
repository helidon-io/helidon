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
 * Thrown to indicate that a message contains illegal argot word.
 */
public class ProfanityException extends IllegalArgumentException {

    private final String profanity;

    /**
     * Creates new instance.
     *
     * @param profanity an illegal argot word.
     */
    public ProfanityException(String profanity) {
        super("Do not use such an ugly word as '" + obfuscate(profanity) + "'!");
        this.profanity = profanity;
    }

    /**
     * Returns an illegal argot word!
     *
     * @return an argot word
     */
    public String getProfanity() {
        return profanity;
    }

    /**
     * Returns an illegal argot word in obfuscated form!
     *
     * @return an argot word in obfuscated form
     */
    public String getObfuscatedProfanity() {
        return obfuscate(profanity);
    }

    private static String obfuscate(String argot) {
        if (argot == null || argot.length() < 2) {
            return argot;
        }
        if (argot.length() == 2) {
            return argot.charAt(0) + "*";
        }
        return argot.charAt(0) + "*" + argot.charAt(argot.length() - 1);
    }
}
