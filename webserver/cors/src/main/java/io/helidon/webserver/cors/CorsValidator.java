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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class CorsValidator {
    private final String socketName;
    private final List<CorsPathValidator> validators;

    CorsValidator(String socketName, List<CorsPathValidator> validators) {
        this.socketName = socketName;
        this.validators = validators;
    }

    static CorsValidator create(CorsConfig config, String socketName) {
        List<CorsPathValidator> validators = new ArrayList<>();
        Set<String> usedPatterns = new HashSet<>();

        for (CorsPathConfig path : config.paths()) {
            // eliminate duplicities (config overriding discovered path config)
            // and at the same time eliminate disabled entries
            // we could keep them in, as the config override is called first, and it never reaches the discovered one
            // but this is cleaner and faster
            if (usedPatterns.add(path.pathPattern())) {
                validators.add(CorsPathValidator.create(path));
            }
        }

        return new CorsValidator(socketName, validators);
    }

    static boolean isPreflight(ServerRequest req) {
        if (req.prologue().method() != Method.OPTIONS) {
            return false;
        }
        if (nonCorsRequest(req)) {
            return false;
        }
        return req.headers().contains(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD);
    }

    @Override
    public String toString() {
        return "CorsValidator for " + socketName;
    }

    /**
     * Check option method request.
     *
     * @param req request
     * @param res response
     * @return whether the check was a success, and we can invoke a (possible) endpoint
     */
    boolean checkOptions(ServerRequest req, ServerResponse res) {
        if (nonCorsRequest(req)) {
            return true;
        }
        // now we have a CORS request
        if (req.headers().contains(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD)) {
            return preFlightCheck(req, res);
        }
        return flightCheck(req, res);
    }

    /**
     * Check non-Option method request.
     *
     * @param req request
     * @param res response
     * @return whether the check was a success, and we can invoke an endpoint
     */
    boolean checkNonOptions(ServerRequest req, ServerResponse res) {
        if (nonCorsRequest(req)) {
            return true;
        }
        // this cannot be a pre-flight, as it is not an OPTIONS method
        return flightCheck(req, res);
    }

    private static boolean nonCorsRequest(ServerRequest req) {
        // if there is no origin header, or it is same as host, then this is not a CORS request
        var origin = req.headers().first(HeaderNames.ORIGIN);
        if (origin.isEmpty()) {
            return true;
        }
        var hostLocation = hostLocation(req);
        var originLocation = originLocation(origin.get());

        return originLocation.equals(hostLocation);
    }

    private static String originLocation(String origin) {
        // we must always have port to align with host handling
        // i.e. http://example.com -> http://example.com:80
        // https://example.com -> https://example.com:443
        int endOfScheme = origin.indexOf(':');
        int lastColon = origin.lastIndexOf(':');

        if (endOfScheme == -1 || endOfScheme != lastColon) {
            // there are either no colons, or two colons in the origin, assume port is present
            return origin;
        }
        String scheme = origin.substring(0, endOfScheme);
        return origin + ":" + ("https".equals(scheme) ? "443" : "80");
    }

    private static String hostLocation(ServerRequest req) {
        var uri = req.requestedUri();
        return uri.scheme() + "://" + uri.host() + ":" + uri.port();
    }

    private boolean flightCheck(ServerRequest req, ServerResponse res) {
        for (CorsPathValidator validator : validators) {
            var result = validator.flight(req, res);
            if (result.matched()) {
                return result.shouldContinue();
            }
        }
        // this is for backward compatibility
        // we may have routes registered that handle CORS
        // allow here, check in our last-resort route in io.helidon.webserver.cors.CorsFeature.CorsOptionsHttpFeature
        return true;
        // once we remove the deprecated code, find all cases with "this is for backward compatibility" and remove them
        // and uncomment the code as needed
        // this is unexpected - if we encounter a CORS request, there MUST be a validator that is registered on /*
        // and forbids all - why was it not invoked?
        // throw new IllegalStateException("Failed to validated CORS in request.");
    }

    private boolean preFlightCheck(ServerRequest req, ServerResponse res) {
        for (CorsPathValidator validator : validators) {
            var result = validator.preFlight(req, res);
            if (result.matched()) {
                return result.shouldContinue();
            }
        }
        // this is for backward compatibility
        // we may have routes registered that handle CORS
        // allow here, check in our last-resort route in io.helidon.webserver.cors.CorsFeature.CorsOptionsHttpFeature
        return true;
        // once we remove the deprecated code, find all cases with "this is for backward compatibility" and remove them
        // and uncomment the code as needed
        // this is unexpected - if we encounter a CORS request, there MUST be a validator that is registered on /*
        // and forbids all - why was it not invoked?
        // throw new IllegalStateException("Failed to validated CORS in request.");
    }
}
