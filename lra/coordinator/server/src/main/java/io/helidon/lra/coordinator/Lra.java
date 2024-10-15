/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
package io.helidon.lra.coordinator;

import java.util.List;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

/**
 * Long Running Action managed by coordinator.
 */
public interface Lra {
    /**
     * LRA header name.
     */
    HeaderName LRA_HTTP_CONTEXT_HEADER_NAME = HeaderNames.create(LRA_HTTP_CONTEXT_HEADER);
    /**
     * LRA ended header name.
     */
    HeaderName LRA_HTTP_ENDED_CONTEXT_HEADER_NAME = HeaderNames.create(LRA_HTTP_ENDED_CONTEXT_HEADER);
    /**
     * LRA parent header name.
     */
    HeaderName LRA_HTTP_PARENT_CONTEXT_HEADER_NAME = HeaderNames.create(LRA_HTTP_PARENT_CONTEXT_HEADER);
    /**
     * LRA recovery header name.
     */
    HeaderName LRA_HTTP_RECOVERY_HEADER_NAME = HeaderNames.create(LRA_HTTP_RECOVERY_HEADER);

    /**
     * ID of the LRA used by this coordinator.
     *
     * @return lraId without coordinator URI prefix
     */
    String lraId();

    /**
     * LRA ID of the parent LRA if this LRA has any.
     *
     * @return id of parent LRA or null
     */
    String parentId();

    /**
     * Returns true if this LRA has parent LRA.
     *
     * @return true if LRA has parent
     */
    boolean isChild();

    /**
     * Returns exact time when will LRA timeout in millis.
     *
     * @return time of timeout in millis
     */
    long timeout();

    /**
     * All participants enrolled in this LRA.
     *
     * @return list of participants enrolled in this LRA
     */
    List<Participant> participants();

    /**
     * Status of this LRA.
     *
     * @return status
     */
    LRAStatus status();
}
