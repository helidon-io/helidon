/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

/**
 * TLS alert name mappings used by the QUIC implementation.
 */
final class SslAlerts {
    private static final String[] ALERT_NAMES = new String[121];

    static {
        ALERT_NAMES[0] = "close_notify";
        ALERT_NAMES[10] = "unexpected_message";
        ALERT_NAMES[20] = "bad_record_mac";
        ALERT_NAMES[21] = "decryption_failed";
        ALERT_NAMES[22] = "record_overflow";
        ALERT_NAMES[30] = "decompression_failure";
        ALERT_NAMES[40] = "handshake_failure";
        ALERT_NAMES[41] = "no_certificate";
        ALERT_NAMES[42] = "bad_certificate";
        ALERT_NAMES[43] = "unsupported_certificate";
        ALERT_NAMES[44] = "certificate_revoked";
        ALERT_NAMES[45] = "certificate_expired";
        ALERT_NAMES[46] = "certificate_unknown";
        ALERT_NAMES[47] = "illegal_parameter";
        ALERT_NAMES[48] = "unknown_ca";
        ALERT_NAMES[49] = "access_denied";
        ALERT_NAMES[50] = "decode_error";
        ALERT_NAMES[51] = "decrypt_error";
        ALERT_NAMES[60] = "export_restriction";
        ALERT_NAMES[70] = "protocol_version";
        ALERT_NAMES[71] = "insufficient_security";
        ALERT_NAMES[80] = "internal_error";
        ALERT_NAMES[86] = "inappropriate_fallback";
        ALERT_NAMES[90] = "user_canceled";
        ALERT_NAMES[100] = "no_renegotiation";
        ALERT_NAMES[109] = "missing_extension";
        ALERT_NAMES[110] = "unsupported_extension";
        ALERT_NAMES[111] = "certificate_unobtainable";
        ALERT_NAMES[112] = "unrecognized_name";
        ALERT_NAMES[113] = "bad_certificate_status_response";
        ALERT_NAMES[114] = "bad_certificate_hash_value";
        ALERT_NAMES[115] = "unknown_psk_identity";
        ALERT_NAMES[116] = "certificate_required";
        ALERT_NAMES[120] = "no_application_protocol";
    }

    private SslAlerts() {
    }

    static String alertName(int id) {
        String name = null;
        if (id >= 0 && id <= 120) {
            name = ALERT_NAMES[id];
        }
        if (name == null) {
            return "0x" + Integer.toHexString(id);
        }
        return name;
    }
}
