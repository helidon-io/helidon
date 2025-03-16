/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen;

import java.util.Locale;

import io.helidon.declarative.codegen.model.http.HeaderValue;
import io.helidon.declarative.codegen.model.http.HttpStatus;
import io.helidon.service.codegen.FieldHandler;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_NAME;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_NAMES;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_VALUES;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_MEDIA_TYPE;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_METHOD;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_STATUS;

public class FieldHelper {
    public static String ensureHeaderNameConstant(FieldHandler fieldHandler, String headerName) {
        String constantPrefix =
                toConstantName("header_" + headerName);

        return fieldHandler.constant(constantPrefix,
                                     HTTP_HEADER_NAME,
                                     headerName,
                                     content -> content
                                             .addContent(HTTP_HEADER_NAMES)
                                             .addContent(".create(\"")
                                             .addContent(headerName)
                                             .addContent("\")"));
    }

    public static String ensureHeaderValueConstant(FieldHandler fieldHandler, HeaderValue headerValue) {
        return fieldHandler.constant("HEADER_VALUE",
                                     HTTP_HEADER,
                                     headerValue,
                                     content -> content
                                             .addContent(HTTP_HEADER_VALUES)
                                             .addContent(".create(\"")
                                             .addContent(headerValue.name())
                                             .addContent("\", \"")
                                             .addContent(headerValue.value())
                                             .addContent("\")"));
    }

    public static String ensureHttpMediaTypeConstant(FieldHandler fieldHandler,
                                                     String mediaType) {

        return fieldHandler.constant("MEDIA_TYPE",
                                     HTTP_MEDIA_TYPE,
                                     mediaType,
                                     content -> content
                                             .addContent(HTTP_MEDIA_TYPE)
                                             .addContent(".create(\"")
                                             .addContent(mediaType)
                                             .addContent("\")"));
    }

    public static String ensureHttpMethodConstant(FieldHandler fieldHandler,
                                                  String httpMethod) {
        String constantName = toConstantName("ENDPOINT_METHOD_" + httpMethod);
        return fieldHandler.constant(constantName,
                                     HTTP_METHOD,
                                     httpMethod.toUpperCase(Locale.ROOT),
                                     content -> content.addContent(HTTP_METHOD)
                                             .addContent(".create(\"")
                                             .addContent(httpMethod)
                                             .addContent("\""));
    }

    public static String ensureHttpStatusConstant(FieldHandler fieldHandler, HttpStatus httpStatus) {
        return fieldHandler.constant("STATUS_" + httpStatus.code(),
                                     HTTP_STATUS,
                                     httpStatus,
                                     content -> {
                                         content.addContent(HTTP_STATUS)
                                                 .addContent(".create(")
                                                 .addContent(String.valueOf(httpStatus.code()));

                                         httpStatus.reason()
                                                 .ifPresent(reason -> content.addContent(", \"" + reason + "\""));

                                         content.addContent(")");
                                     });
    }
}
