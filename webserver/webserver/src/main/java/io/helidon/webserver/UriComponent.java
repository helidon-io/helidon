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

package io.helidon.webserver;

import java.net.URLDecoder;

import io.helidon.common.http.HashParameters;
import io.helidon.common.http.Parameters;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Extracted from Jersey
 * <p>
 * Utility class for validating, encoding and decoding components of a URI.
 *
 * @author Paul Sandoz
 * @author Marek Potociar (marek.potociar at oracle.com)
 */
final class UriComponent {

    private UriComponent() {
    }

    /**
     * Decode the query component of a URI.
     * <p>
     * Query parameter names in the returned map are always decoded. Decoding of query parameter
     * values can be controlled using the {@code decode} parameter flag.
     *
     * @param query  the query component in encoded form.
     * @param decode {@code true} if the returned query parameter values of the query component
     *               should be in decoded form.
     * @return the multivalued map of query parameters.
     */
    static Parameters decodeQuery(String query, boolean decode) {
        return decodeQuery(query, true, decode);
    }

    /**
     * Decode the query component of a URI.
     * <p>
     * Decoding of query parameter names and values can be controlled using the {@code decodeNames}
     * and {@code decodeValues} parameter flags.
     *
     * @param query        the query component in encoded form.
     * @param decodeNames  {@code true} if the returned query parameter names of the query component
     *                     should be in decoded form.
     * @param decodeValues {@code true} if the returned query parameter values of the query component
     *                     should be in decoded form.
     * @return the multivalued map of query parameters.
     */
    static Parameters decodeQuery(String query, boolean decodeNames, boolean decodeValues) {
        Parameters queryParameters = HashParameters.create();

        if (query == null || query.isEmpty()) {
            return queryParameters;
        }

        int s = 0;
        do {
            int e = query.indexOf('&', s);

            if (e == -1) {
                decodeQueryParam(queryParameters, query.substring(s), decodeNames, decodeValues);
            } else if (e > s) {
                decodeQueryParam(queryParameters, query.substring(s, e), decodeNames, decodeValues);
            }
            s = e + 1;
        } while (s > 0 && s < query.length());

        return queryParameters;
    }

    private static void decodeQueryParam(Parameters params, String param, boolean decodeNames, boolean decodeValues) {
        int equals = param.indexOf('=');
        if (equals > 0) {
            params.add((decodeNames) ? URLDecoder.decode(param.substring(0, equals), UTF_8) : param.substring(0, equals),
                       (decodeValues)
                               ? URLDecoder.decode(param.substring(equals + 1), UTF_8)
                               : param.substring(equals + 1));
        } else if (equals == 0) {
            // no key declared, ignore
            return;
        } else if (!param.isEmpty()) {
            params.add((decodeNames) ? URLDecoder.decode(param, UTF_8) : param, "");
        }
    }
}
