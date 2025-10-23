/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.parameters.Parameters;

final class CookieParser {
    private static final Parameters EMPTY_COOKIES = Parameters.empty("http/cookies");

    private static final String RFC2965_VERSION = "$Version";
    private static final String RFC2965_PATH = "$Path";
    private static final String RFC2965_DOMAIN = "$Domain";
    private static final String RFC2965_PORT = "$Port";

    private CookieParser() {
    }

    /**
     * Parse cookies based on RFC6265 which also accepts older formats including RFC2965 but skips parameters.
     *
     * <p>Multiple cookies can be returned in a single headers and a single cookie-name can have multiple values.
     * Note that base on RFC6265 an order of cookie values has no semantics.
     *
     * @param httpHeader cookie header
     * @return a cookie name and values parsed into a parameter format.
     */
    static Parameters parse(Header httpHeader) {
        Map<String, List<String>> allCookies = new HashMap<>();
        for (String value : httpHeader.allValues()) {
            parse(allCookies, value);
        }
        if (allCookies.isEmpty()) {
            return EMPTY_COOKIES;
        }
        // avoids splitting the component every single time parameters are created, which is
        // for example for every single HTTP request
        return Parameters.create("http/cookies", allCookies, "http", "cookies");
    }

    static Parameters empty() {
        return EMPTY_COOKIES;
    }

    private static void parse(Map<String, List<String>> allCookies, String cookieHeaderValue) {
        // Beware RFC2965
        boolean isRfc2965 = false;
        if (cookieHeaderValue.regionMatches(true, 0, RFC2965_VERSION, 0, RFC2965_VERSION.length())) {
            isRfc2965 = true;
            int ind = cookieHeaderValue.indexOf(';');
            if (ind < 0) {
                return;
            } else {
                cookieHeaderValue = cookieHeaderValue.substring(ind + 1);
            }
        }

        for (String baseToken : HeaderHelper.tokenize(',', cookieHeaderValue)) {
            for (String token : HeaderHelper.tokenize(';', baseToken)) {
                int eqInd = token.indexOf('=');
                if (eqInd > 0) {
                    String name = token.substring(0, eqInd).trim();
                    if (name.isEmpty()) {
                        continue; // Name MOST NOT be empty;
                    }
                    if (isRfc2965 && name.charAt(0) == '$'
                            && (
                            RFC2965_PATH.equalsIgnoreCase(name) || RFC2965_DOMAIN.equalsIgnoreCase(name)
                                    || RFC2965_PORT.equalsIgnoreCase(name) || RFC2965_VERSION.equalsIgnoreCase(name))) {
                        continue; // Skip RFC2965 attributes
                    }
                    String value = token.substring(eqInd + 1).trim();
                    allCookies.computeIfAbsent(name, it -> new ArrayList<>(1)).add(HeaderHelper.unwrap(value));
                }
            }
        }
    }

}
