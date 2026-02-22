/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.cors;

import io.helidon.http.Method;
import io.helidon.http.NotFoundException;
import io.helidon.http.Status;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

class CorsHttpFilter implements Filter {
    private final CorsValidator validator;
    private final String socketName;

    CorsHttpFilter(CorsValidator validator, String socketName) {
        this.validator = validator;
        this.socketName = socketName;
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if (req.prologue().method() == Method.OPTIONS) {
            optionsMethod(chain, req, res);
        } else {
            nonOptionsMethod(chain, req, res);
        }
    }

    @Override
    public String toString() {
        return "CORS filter for " + socketName;
    }

    private void nonOptionsMethod(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if (validator.checkNonOptions(req, res)) {
            chain.proceed();
        }
    }

    private void optionsMethod(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if (validator.checkOptions(req, res)) {
            try {
                chain.proceed();
            } catch (NotFoundException ignored) {
                // assume user code (such as filter) has thrown this - ignore, as this still means we should return pre-flight
                // in case of a pre-flight check that is not served by any endpoint, we must send the response to options request
                if (CorsValidator.isPreflight(req)) {
                    res.status(Status.OK_200)
                            .send();
                }
            }
        }
    }
}
