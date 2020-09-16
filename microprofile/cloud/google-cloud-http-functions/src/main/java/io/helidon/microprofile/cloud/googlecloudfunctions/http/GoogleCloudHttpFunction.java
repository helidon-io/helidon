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
package io.helidon.microprofile.cloud.googlecloudfunctions.http;

import io.helidon.microprofile.cloud.common.CommonCloudFunction;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

/**
 * Helidon Google Cloud Function implementation of com.google.cloud.functions.HttpFunction.
 * This is the class that should be specified as entry point in gcloud
 *
 */
public class GoogleCloudHttpFunction extends CommonCloudFunction<HttpFunction> implements HttpFunction {

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        delegate().service(request, response);
    }

}
