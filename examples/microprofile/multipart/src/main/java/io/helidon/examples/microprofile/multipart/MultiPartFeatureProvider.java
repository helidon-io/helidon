/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.microprofile.multipart;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.media.multipart.MultiPartFeature;

/**
 * {@link MultiPartFeature} is not auto-discovered. This {@link Feature} is discovered with {@link @Provider}
 * and registers {@link MultiPartFeature} manually.
 */
@Provider
public class MultiPartFeatureProvider implements Feature {

    @Override
    public boolean configure(FeatureContext context) {
        return new MultiPartFeature().configure(context);
    }
}
