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

package io.helidon.security.jersey;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Configuration of a Jersey security feature.
 */
class FeatureConfig {
    private final boolean debug;
    private final boolean authorizeAnnotatedOnly;
    private final boolean usePrematching;
    private final List<QueryParamHandler> queryParamHandlers = new LinkedList<>();

    FeatureConfig() {
        this.authorizeAnnotatedOnly = false;
        this.debug = false;
        this.usePrematching = false;
    }

    FeatureConfig(boolean authorizeAnnotatedOnly,
                  List<QueryParamHandler> queryParamHandlers,
                  boolean debug,
                  boolean usePrematching) {
        this.authorizeAnnotatedOnly = authorizeAnnotatedOnly;
        this.queryParamHandlers.addAll(queryParamHandlers);
        this.debug = debug;
        this.usePrematching = usePrematching;
    }

    public boolean shouldAuthorizeAnnotatedOnly() {
        return authorizeAnnotatedOnly;
    }

    public List<QueryParamHandler> getQueryParamHandlers() {
        return Collections.unmodifiableList(queryParamHandlers);
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean shouldUsePrematching() {
        return usePrematching;
    }

    @Override
    public String toString() {
        return "FeatureConfig(" + authorizeAnnotatedOnly + ", " + queryParamHandlers.size() + ", debug:" + debug + ")";
    }
}
