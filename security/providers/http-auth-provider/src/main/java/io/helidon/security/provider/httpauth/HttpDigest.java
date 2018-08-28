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

package io.helidon.security.provider.httpauth;

import java.util.Arrays;

import io.helidon.config.Config;

/**
 * Digest specific enums.
 */
public class HttpDigest {
    /**
     * Http digest algorithm.
     * See <a href="https://tools.ietf.org/html/rfc2617#page-9">https://tools.ietf.org/html/rfc2617#page-9</a>.
     * Only {@link #MD5} is supported.
     */
    public enum Algorithm {
        /**
         * MD5 algorithm.
         */
        MD5("MD5");

        private final String algorithm;

        Algorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        /**
         * Parse configuration into this enum.
         *
         * @param config Config with wrapped enum value
         * @return enum instance
         */
        public static Algorithm fromConfig(Config config) {
            return fromString(config.asString());
        }

        static Algorithm fromString(String value) {
            if (null == value) {
                // incoming request did not contain an algorithm - MD5 is default
                return MD5;
            }
            for (Algorithm algorithm : Algorithm.values()) {
                if (algorithm.getAlgorithm().equals(value)) {
                    return algorithm;
                }
            }
            throw new IllegalArgumentException("Invalid algorithm for digest: " + value + ", allowed: " + Arrays
                    .toString(Algorithm.values()));
        }

        /**
         * Get the algorithm string.
         *
         * @return algorithm string as used in RFC
         */
        public String getAlgorithm() {
            return algorithm;
        }
    }

    /**
     * Http digest QOP (quality of protection).
     * See <a href="https://tools.ietf.org/html/rfc2617#page-9">https://tools.ietf.org/html/rfc2617#page-9</a>.
     */
    public enum Qop {
        /**
         * Legacy approach - used internally to parse headers. Do not use this option when
         * building provider. If you want to support only legacy RFC, please use
         * {@link HttpDigestAuthProvider.Builder#noDigestQop()}.
         * Only {@link #AUTH} is supported, as auth-int requires access to message body.
         */
        NONE("none"),
        /**
         * QOP "auth" - stands for "authentication".
         */
        AUTH("auth");

        private final String qop;

        Qop(String qop) {
            this.qop = qop;
        }

        /**
         * Parse configuration into this enum.
         *
         * @param config Config with wrapped enum value
         * @return enum instance
         */
        public static Qop fromConfig(Config config) {
            return fromString(config.asString());
        }

        static Qop fromString(String value) {
            if (null == value) {
                return NONE;
            }
            for (Qop qop : Qop.values()) {
                if (qop.getQop().equals(value)) {
                    return qop;
                }
            }
            throw new IllegalArgumentException("Invalid QOP for digest: " + value + ", allowed: " + Arrays
                    .toString(Qop.values()));
        }

        /**
         * Get the QOP string.
         *
         * @return QOP string as defined by the RFC
         */
        public String getQop() {
            return qop;
        }
    }
}
