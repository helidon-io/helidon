/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver;

import io.helidon.common.context.Context;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;

/**
 * Server context.
 * Provides elements that are configured on server level and shared by all listeners and all connections.
 */
public interface ServerContext {
    /**
     * Create a new server context.
     *
     * @param context context to act as a parent for all request contexts
     * @param mediaContext media context
     * @param contentEncodingContext content encoding context (gzip, deflate etc.)
     * @return a new server context
     */
    static ServerContext create(Context context,
                                MediaContext mediaContext,
                                ContentEncodingContext contentEncodingContext) {
        return new ServerContextImpl(context, mediaContext, contentEncodingContext);
    }

    /**
     * Server context configured as the top level parents of all request context.
     *
     * @return server context, always available
     */
    Context context();

    /**
     * Media context to read and write typed entities.
     *
     * @return media context
     */
    MediaContext mediaContext();

    /**
     * Content encoding support, to handle entity encoding (such as gzip, deflate).
     *
     * @return content encoding support
     */
    ContentEncodingContext contentEncodingContext();
}
