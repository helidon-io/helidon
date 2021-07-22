/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriInfo;

import io.helidon.config.Config;
import io.helidon.security.QueryParamMapping;
import io.helidon.security.util.TokenHandler;

/**
 * Handler of query parameters - extracts them and stores
 * them in a security header, so security can access them.
 */
public final class QueryParamHandler {
    private final String paramName;
    private final TokenHandler tokenHandler;

    private QueryParamHandler(QueryParamMapping mapping) {
        this.paramName = mapping.queryParamName();
        this.tokenHandler = mapping.tokenHandler();
    }

    /**
     * Read a new instance from configuration.
     *
     * @param config configuration instance
     * @return new query parameter handler instance
     */
    public static QueryParamHandler create(Config config) {
        return new QueryParamHandler(QueryParamMapping.create(config));
    }

    /**
     * Create instance from an existing mapping.
     *
     * @param mapping mapping to use
     * @return new query parameter handler instance
     */
    public static QueryParamHandler create(QueryParamMapping mapping) {
        return new QueryParamHandler(mapping);
    }

    void extract(UriInfo uriInfo, Map<String, List<String>> headers) {
        List<String> values = uriInfo.getQueryParameters().get(paramName);
        if (null == values) {
            return;
        }

        values.forEach(token -> {
            String tokenValue = tokenHandler.extractToken(token);
            tokenHandler.addHeader(headers, tokenValue);
                       }
        );
    }

}
