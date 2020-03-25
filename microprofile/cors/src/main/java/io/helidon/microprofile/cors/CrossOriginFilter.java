/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cors;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

import io.helidon.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import static io.helidon.microprofile.cors.CrossOriginConfig.CrossOriginConfigMapper;
import static io.helidon.microprofile.cors.CrossOriginHelper.RequestType.CORS;
import static io.helidon.microprofile.cors.CrossOriginHelper.RequestType.NORMAL;
import static io.helidon.microprofile.cors.CrossOriginHelper.RequestType.PREFLIGHT;
import static io.helidon.microprofile.cors.CrossOriginHelper.findRequestType;
import static io.helidon.microprofile.cors.CrossOriginHelper.processCorsRequest;
import static io.helidon.microprofile.cors.CrossOriginHelper.processCorsResponse;
import static io.helidon.microprofile.cors.CrossOriginHelper.processPreFlight;

/**
 * Class CrossOriginFilter.
 */
@Priority(Priorities.HEADER_DECORATOR)
class CrossOriginFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Context
    private ResourceInfo resourceInfo;

    private List<CrossOriginConfig> crossOriginConfigs;

    CrossOriginFilter() {
        Config config = (Config) ConfigProvider.getConfig();
        crossOriginConfigs = config.get("cors").as(new CrossOriginConfigMapper()).get();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        CrossOriginHelper.RequestType type = findRequestType(requestContext);
        if (type != NORMAL) {
            if (type == PREFLIGHT) {
                Response response = processPreFlight(requestContext, resourceInfo, crossOriginConfigs);
                requestContext.abortWith(response);
            } else if (type == CORS) {
                Optional<Response> response = processCorsRequest(requestContext, resourceInfo, crossOriginConfigs);
                response.ifPresent(requestContext::abortWith);
            } else {
                throw new IllegalStateException("Invalid value for enum RequestType");
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        CrossOriginHelper.RequestType type = findRequestType(requestContext);
        if (type == CORS) {
            processCorsResponse(requestContext, responseContext, resourceInfo, crossOriginConfigs);
        }
    }
}
