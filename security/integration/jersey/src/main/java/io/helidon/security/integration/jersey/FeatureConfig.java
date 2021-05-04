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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Configuration of a Jersey security feature.
 */
class FeatureConfig {
    static final boolean DEFAULT_DEBUG = false;
    static final boolean DEFAULT_ATZ_ANNOTATED_ONLY = false;
    static final boolean DEFAULT_ATN_ANNOTATED_ONLY = true;
    static final boolean DEFAULT_PREMATCHING_ATN = false;
    static final boolean DEFAULT_PREMATCHING_ATZ = false;
    static final boolean DEFAULT_USE_ABORT_WITH = true;

    private final boolean debug;
    private final boolean authorizeAnnotatedOnly;
    private final boolean usePrematchingAtn;
    private final boolean usePrematchingAtz;
    private final List<QueryParamHandler> queryParamHandlers = new LinkedList<>();
    private final boolean authenticateAnnotatedOnly;
    private final boolean useAbortWith;

    FeatureConfig() {
        this.debug = DEFAULT_DEBUG;
        this.authorizeAnnotatedOnly = DEFAULT_ATZ_ANNOTATED_ONLY;
        this.usePrematchingAtn = DEFAULT_PREMATCHING_ATN;
        this.usePrematchingAtz = DEFAULT_PREMATCHING_ATZ;
        this.authenticateAnnotatedOnly = DEFAULT_ATN_ANNOTATED_ONLY;
        this.useAbortWith = DEFAULT_USE_ABORT_WITH;
    }

    FeatureConfig(SecurityFeature.Builder builder) {
        this.debug = builder.isDebug();
        this.authorizeAnnotatedOnly = builder.isAuthorizeAnnotatedOnly();
        this.authenticateAnnotatedOnly = builder.isAuthenticateAnnotatedOnly();
        this.usePrematchingAtz = builder.isPrematchingAuthorization();
        if (this.usePrematchingAtz) {
            this.usePrematchingAtn = true;
        } else {
            this.usePrematchingAtn = builder.isPrematchingAuthentication();
        }

        this.queryParamHandlers.addAll(builder.queryParamHandlers());
        this.useAbortWith = builder.useAbortWith();
    }

    boolean shouldAuthorizeAnnotatedOnly() {
        return authorizeAnnotatedOnly;
    }

    boolean shouldAuthenticateAnnotatedOnly() {
        return authenticateAnnotatedOnly;
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

    public boolean useAbortWith() {
        return useAbortWith;
    }

    @Override
    public String toString() {
        return "FeatureConfig(" + authorizeAnnotatedOnly + ", " + queryParamHandlers.size() + ", debug:" + debug + ")";
    }
}
