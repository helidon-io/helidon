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

package io.helidon.webserver.observe.metrics;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.Method;
import io.helidon.http.MethodPredicate;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;

/**
 * Settings for path-based automatic metrics configuration.
 * <p>
 * An HTTP request matches a path entry if the request path matches the entry's path pattern and the request's HTTP method
 * matches one of the entry's methods. If there no {@code methods} list for the entry, then all HTTP methods match the entry.
 * <p>
 * If a request matches an entry, then the entry's {@code enabled} value (which defaults to {@code}) determines the entry's vote
 * whether the request should be measured. If a request matches multiple entries, the vote of the last matched entry wins.
 *
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = AutoHttpMetricsPathConfigSupport.BuilderDecorator.class)
@Prototype.CustomMethods(AutoHttpMetricsPathConfigSupport.CustomMethods.class)
interface AutoHttpMetricsPathConfigBlueprint {

    /**
     * Creates a metrics path config that disables automatic metrics collection for the specified exact path.
     *
     * @param path exact path to exclude
     * @return metrics path config disabling collection for the path
     */
    static AutoHttpMetricsPathConfig disabled(String path) {
        return AutoHttpMetricsPathConfig.builder()
                .enabled(false)
                .path(PathMatchers.exact(path))
                .build();
    }

    /**
     * Whether automatic metrics are to be enabled for requests which match the specified {@link io.helidon.http.PathMatcher}
     * and HTTP methods.
     *
     * @return whether auto metrics are to be enabled for this path config's path matcher and HTTP methods
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Path matcher for this path config for which this path config applies.
     *
     * @return path matcher
     */
    @Option.Configured
    PathMatcher path();

    /**
     * HTTP methods for which this path config applies; default is to match all HTTP methods.
     *
     * @return HTTP methods
     */
    @Option.Configured
    @Option.Singular
    List<Method> methods();

    /**
     * Method predicate for checking a request's HTTP method against this config's methods.
     *
     * @return method predicate for the configured HTTP methods
     * @hidden internal use only
     */
    @Option.Access("")
    MethodPredicate methodPredicate();

}
