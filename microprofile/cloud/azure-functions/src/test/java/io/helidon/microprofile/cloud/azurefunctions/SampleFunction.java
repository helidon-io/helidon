/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cloud.azurefunctions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class SampleFunction extends AzureCloudFunction<String, Integer> {

    @Inject
    private MyService myService;

    @FunctionName("getHashCode")
    public Integer execute(@HttpTrigger(name = "req", methods = {HttpMethod.GET,
            HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        ExecutionContext context) {
        return super.handleRequest(request.getBody().get(), context);
    }

    @Override
    protected Integer execute(String input, ExecutionContext context) {
        return myService.hashCode(input);
    }

    @ApplicationScoped
    public static class MyService {

        public int hashCode(String str) {
            return str.hashCode();
        }

    }

}
