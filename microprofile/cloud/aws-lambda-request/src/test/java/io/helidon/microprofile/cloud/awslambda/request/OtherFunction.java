/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cloud.awslambda.request;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.helidon.microprofile.cloud.common.CloudFunction;

import javax.enterprise.context.ApplicationScoped;

@CloudFunction
@ApplicationScoped
public class OtherFunction implements RequestHandler<String, Integer> {

    @Override
    public Integer handleRequest(String input, Context context) {
        throw new UnsupportedOperationException("This function is not in the microprofile-config.properties");
    }

}
