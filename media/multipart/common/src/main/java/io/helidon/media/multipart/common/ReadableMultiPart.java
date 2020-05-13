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
package io.helidon.media.multipart.common;

import java.util.List;

/**
 * Readable multipart message.
 */
public final class ReadableMultiPart implements MultiPart<ReadableBodyPart> {

    private final List<ReadableBodyPart> parts;

    /**
     * Create a new readable multipart instance.
     * @param parts body parts
     */
    ReadableMultiPart(List<ReadableBodyPart> parts) {
        this.parts = parts;
    }

    @Override
    public List<ReadableBodyPart> bodyParts() {
        return parts;
    }
}
