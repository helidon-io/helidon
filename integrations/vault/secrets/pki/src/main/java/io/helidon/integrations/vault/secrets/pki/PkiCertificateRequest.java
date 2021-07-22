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

package io.helidon.integrations.vault.secrets.pki;

import java.time.Duration;

import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

abstract class PkiCertificateRequest<T extends PkiCertificateRequest<T>> extends VaultRequest<T> {
    private PkiFormat format = PkiFormat.DER;
    private String roleName;

    /**
     * Specifies requested Subject Alternative Name(s).
     * These can be host names or email addresses; they will be parsed into their respective fields. If any requested names do
     * not match role policy, the entire request will be denied.
     *
     * @param name alt name
     * @return updated request
     */
    public T addAltName(String name) {
        return addToCommaDelimitedArray("alt_names", name);
    }

    /**
     * Specifies requested IP Subject Alternative Name(s).
     * Only valid if the role allows IP SANs (which is the default).
     *
     * @param subjectAlternativeName IP subject alternative name
     * @return updated request
     */
    public T addIpSan(String subjectAlternativeName) {
        return addToCommaDelimitedArray("ip_sans", subjectAlternativeName);
    }

    /**
     *  Specifies the requested URI Subject Alternative Name(s).
     *
     * @param subjectAlternativeName URI subject alternative name
     * @return updated request
     */
    public T addUriSan(String subjectAlternativeName) {
        return addToCommaDelimitedArray("uri_sans", subjectAlternativeName);
    }

    /**
     *  Specifies custom OID/UTF8-string SANs. These must match values specified on the role in allowed_other_sans (see role
     *  creation for allowed_other_sans globbing rules). The format is the same as OpenSSL:
     *  &lt;oid&gt;:&lt;type&gt;:&lt;value&gt; where the type is hardcoded to UTF8.
     *
     * @param oid OID of the subject alternative name
     * @param value value of the subject alternative name
     * @return updated request
     */
    public T addOtherSan(String oid, String value) {
        return addToCommaDelimitedArray("other_sans", oid + ";UTF-8;" + value);
    }

    /**
     * Specifies requested Time To Live. Cannot be greater than the role's max_ttl value. If not provided, the role's ttl value
     * will be used. Note that the role values default to system values if not explicitly set.
     *
     * @param duration time to live
     * @return updated request
     */
    public T ttl(Duration duration) {
        return add("ttl", duration);
    }

    /**
     * If true, the given common_name will not be included in DNS or Email Subject Alternate Names (as appropriate). Useful if
     * the CN is not a hostname or email address, but is instead some human-readable identifier.
     *
     * @param exclude whether to exclude CN from subject alternative names
     * @return updated request
     */
    public T excludeCnFromSans(boolean exclude) {
        return add("exclude_cn_from_sans", exclude);
    }

    public T commonName(String commonName) {
        return add("common_name", commonName);
    }

    public T format(PkiFormat format) {
        return format(format.vaultType());
    }

    public T roleName(String roleName) {
        this.roleName = roleName;
        return me();
    }

    T format(String format) {
        return add("format", format);
    }

    PkiFormat format() {
        return format;
    }

    String roleName() {
        if (roleName == null) {
            throw new VaultApiException("Certificate request role name must be defined");
        }
        return roleName;
    }
}
