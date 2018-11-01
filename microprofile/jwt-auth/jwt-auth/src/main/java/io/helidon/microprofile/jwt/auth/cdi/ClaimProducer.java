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

package io.helidon.microprofile.jwt.auth.cdi;

import org.eclipse.microprofile.jwt.Claim;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.json.JsonString;

/**
 * Class MetricProducer.
 */
@RequestScoped
public class ClaimProducer {

    @Produces
    @Claim
    public String produceClaim(InjectionPoint ip) {
        Claim claim = ip.getAnnotated().getAnnotation(Claim.class);
        return "";
    }

    @Produces
    @Claim
    public JsonString produceJsonClaim(InjectionPoint ip) {
        Claim claim = ip.getAnnotated().getAnnotation(Claim.class);
        return null;
    }


}
