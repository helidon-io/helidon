/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.microprofile.metrics;

import javax.enterprise.context.Dependent;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.eclipse.microprofile.metrics.annotation.Counted;

@Dependent
public class ResourceWithReusedMetricForInvocation {

    static final String TAG_1 = "tag_1=value_1";
    static final String TAG_2 = "tag_2=value_2";
    static final String OTHER_REUSED_NAME = "reusedRetrieveDelay";

    @GET
    @Path("method1")
    @Counted(name = OTHER_REUSED_NAME, absolute=true,reusable = true, tags = {TAG_1, TAG_2})
    public String method1() {return "Hi from method 1";}

    @GET
    @Path("method2")
    @Counted(name = OTHER_REUSED_NAME, absolute=true, reusable = true, tags = {TAG_1, TAG_2})
    public String method2() {return "Hi from method 2";}
}
