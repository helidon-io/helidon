/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import io.helidon.media.common.MessageBodyContent;

/**
 * Body part entity.
 *
 * @see ReadableBodyPart
 * @see WriteableBodyPart
 */
public interface BodyPart {

    /**
     * Get the reactive representation of the part content.
     * @return {@link MessageBodyContent}, never {@code null}
     */
    MessageBodyContent content();

    /**
     * Returns HTTP part headers.
     * @return BodyPartHeaders, never {@code null}
     */
    BodyPartHeaders headers();

    /**
     * Get the control name.
     *
     * @return the {@code name} parameter of the {@code Content-Disposition}
     * header, or {@code null} if not present.
     */
    default String name() {
        return headers().contentDisposition().name().orElse(null);
    }

    /**
     * Get the file name.
     *
     * @return the {@code filename} parameter of the {@code Content-Disposition}
     * header, or {@code null} if not present.
     */
    default String filename() {
        return headers().contentDisposition().filename().orElse(null);
    }
}
