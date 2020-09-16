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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.Context;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class AWSLambdaRequestFunctionTest {

    @Test
    public void example() {
        String input = "This is a test string";
        int expected = input.length();
        Context context = Mockito.mock(Context.class);
        int result = new AWSLambdaRequestFunction<String, Integer>().handleRequest(input, context);
        assertEquals(expected, result);
    }

}
