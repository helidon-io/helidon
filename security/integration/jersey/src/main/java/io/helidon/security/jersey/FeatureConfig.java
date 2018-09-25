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
    private final boolean usePrematchingAtn;
    private final boolean usePrematchingAtz;
    private final List<QueryParamHandler> queryParamHandlers = new LinkedList<>();

    FeatureConfig() {
        this.debug = false;
        this.authorizeAnnotatedOnly = false;
        this.usePrematchingAtn = false;
        this.usePrematchingAtz = false;
    }

    FeatureConfig(SecurityFeature.Builder builder) {
        this.debug = builder.isDebug();
        this.authorizeAnnotatedOnly = builder.isAuthorizeAnnotatedOnly();
        this.usePrematchingAtz = builder.isPrematchingAuthorization();
        if (this.usePrematchingAtz) {
            this.usePrematchingAtn = true;
        } else {
            this.usePrematchingAtn = builder.isPrematchingAuthentication();
        }

        this.queryParamHandlers.addAll(builder.getQueryParamHandlers());
    }

    boolean shouldAuthorizeAnnotatedOnly() {
        return authorizeAnnotatedOnly;
    }

    List<QueryParamHandler> getQueryParamHandlers() {
        return Collections.unmodifiableList(queryParamHandlers);
    }

    boolean isDebug() {
        return debug;
    }

    public boolean shouldUsePrematchingAuthentication() {
        return usePrematchingAtn;
    }

    public boolean shouldUsePrematchingAuthorization() {
        return usePrematchingAtz;
    }

    @Override
    public String toString() {
        return "FeatureConfig(" + authorizeAnnotatedOnly + ", " + queryParamHandlers.size() + ", debug:" + debug + ")";
    }
}
