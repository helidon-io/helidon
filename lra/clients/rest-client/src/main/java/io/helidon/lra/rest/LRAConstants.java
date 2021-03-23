/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.lra.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public final class LRAConstants {
    public static final String COORDINATOR_PATH_NAME = "lra-coordinator";
    public static final String RECOVERY_COORDINATOR_PATH_NAME = "recovery";

    public static final String COMPLETE = "complete";
    public static final String COMPENSATE = "compensate";
    public static final String STATUS = "status";
    public static final String LEAVE = "leave";
    public static final String AFTER = "after";
    public static final String FORGET = "forget";

    public static final String STATUS_PARAM_NAME = "Status";
    public static final String CLIENT_ID_PARAM_NAME = "ClientID";
    public static final String TIMELIMIT_PARAM_NAME = "TimeLimit";
    public static final String PARENT_LRA_PARAM_NAME = "ParentLRA";
    public static final String QUERY_PAIR_SEPARATOR = "&"; // separator to isolate each "key=value" pair of a URI query component
    public static final String QUERY_FIELD_SEPARATOR = "="; // separator to pick out the key and value of each pair
    public static final String RECOVERY_PARAM = "recoveryCount";
    public static final String HTTP_METHOD_NAME = "method"; // the name of the HTTP method used to invoke participants

    /**
     * Number of seconds to wait for requests to participant.
     * The timeout is hardcoded as the protocol expects retry in case of failure and timeout.
     */
    public static final long PARTICIPANT_TIMEOUT = 2;

    private static final Pattern UID_REGEXP_EXTRACT_MATCHER = Pattern.compile(".*/([^/?]+).*");

    private LRAConstants() {
        // utility class
    }

    /**
     * Extract the uid part from an LRA id.
     *
     * @param lraId  LRA id to extract the uid from
     * @return  uid of LRA
     */
    public static String getLRAUid(String lraId) {
        return lraId == null ? null : UID_REGEXP_EXTRACT_MATCHER.matcher(lraId).replaceFirst("$1");
    }

    /**
     * Extract the uid part from an LRA id.
     *
     * @param lraId  LRA id to extract the uid from
     * @return  uid of LRA
     */
    public static String getLRAUid(URI lraId) {
        if (lraId == null) return null;
        String path = lraId.getPath();
        if (path == null) return null;
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public static URI getLRACoordinatorUrl(URI lraId) {
        if (lraId == null) return null;
        String lraIdPath = lraId.getPath();
        String lraCoordinatorPath = lraIdPath.substring(0, lraIdPath.lastIndexOf(COORDINATOR_PATH_NAME)) + COORDINATOR_PATH_NAME;
        try {
            return new URI(lraId.getScheme(), lraId.getUserInfo(), lraId.getHost(), lraId.getPort(), lraCoordinatorPath,
                    null, null);
        } catch (URISyntaxException use) {
            throw new IllegalStateException("Cannot construct URI from the LRA coordinator URL path '" + lraCoordinatorPath
                    + "' extracted from the LRA id URI '" + lraId + "'");
        }
    }
}
