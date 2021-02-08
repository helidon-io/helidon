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
package io.helidon.microprofile.cloud.awslambda.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.helidon.microprofile.cloud.common.CommonCloudFunction;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

/**
 * Helidon AWS Lambda Stream Function implementation of com.amazonaws.services.lambda.runtime.RequestStreamHandler.
 * This is the class that should be specified as entry point in AWS
 *
 */
public class AWSLambdaStreamFunction extends CommonCloudFunction<RequestStreamHandler> implements RequestStreamHandler {

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        delegate().handleRequest(input, output, context);
    }

}
