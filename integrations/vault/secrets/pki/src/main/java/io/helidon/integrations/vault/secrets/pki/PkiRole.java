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
import java.util.List;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Pki Role request and response.
 */
public final class PkiRole {
    private PkiRole() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
        private String roleName;

        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(javax.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        /**
         * Specifies the Time To Live value. If not set,
         * uses the system default value or the value of {@link #maxTtl(java.time.Duration)}, whichever is shorter.
         *
         * @param ttl duration to use
         * @return updated request
         */
        public Request ttl(Duration ttl) {
            return add("ttl", ttl);
        }

        /**
         * Specifies the maximum Time To Live. If not set, defaults to the system maximum lease TTL.
         *
         * @param maxTtl duration to use
         * @return updated request
         */
        public Request maxTtl(Duration maxTtl) {
            return add("max_ttl", maxTtl);
        }

        /**
         * Specifies if clients can request certificates for localhost as one of the requested common names. This is useful for
         * testing and to allow clients on a single host to talk securely.
         *
         * @param allowLocalhost whether to allow localhost
         * @return updated request
         */
        public Request allowLocalhost(boolean allowLocalhost) {
            return add("allow_localhost", allowLocalhost);
        }

        /**
         * When set, allowed_domains may contain templates, as with
         * <a href="https://www.vaultproject.io/docs/concepts/policies">ACL Path Templating</a>.
         *
         * @param allowDomainTemplates whether to allow templates in domains
         * @return updated request
         */
        public Request allowedDomainTemplates(boolean allowDomainTemplates) {
            return add("allowed_domain_template", allowDomainTemplates);
        }

        /**
         * Specifies if clients can request certificates matching the value of the actual domains themselves; e.g. if a configured
         * domain set with allowed_domains is example.com, this allows clients to actually request a certificate containing the
         * name example.com as one of the DNS values on the final certificate. In some scenarios, this can be considered a security
         * risk.
         *
         * @param allowBareDomains whether to allow bare domains
         * @return updated request
         */
        public Request allowBareDomains(boolean allowBareDomains) {
            return add("allowed_bare_domains", allowBareDomains);
        }

        /**
         *  Specifies if clients can request certificates with CNs that are subdomains of the CNs allowed by the other role options.
         *  This includes wildcard subdomains. For example, an allowed_domains value of example.com with this option set to true
         *  will allow foo.example.com and bar.example.com as well as *.example.com. This is redundant when using the
         *  allow_any_name option.
         *
         * @param allowSubDomains whether to allow subdomains
         * @return updated request
         */
        public Request allowSubDomains(boolean allowSubDomains) {
            return add("allowed_subdomains", allowSubDomains);
        }

        /**
         * Allows names specified in allowed_domains to contain glob patterns (e.g. ftp*.example.com). Clients will be allowed to
         * request certificates with names matching the glob patterns.
         *
         * @param allowGlobDomains whether to allow glob domains
         * @return updated request
         */
        public Request allowGlobDomains(boolean allowGlobDomains) {
            return add("allowed_glob_domains", allowGlobDomains);
        }

        /**
         * Specifies if clients can request any CN. Useful in some circumstances, but make sure you understand whether it is
         * appropriate for your installation before enabling it.
         *
         * @param allowAnyName whether to allow any name
         * @return updated request
         */
        public Request allowAnyName(boolean allowAnyName) {
            return add("allow_any_name", allowAnyName);
        }

        /**
         * Specifies if only valid host names are allowed for CNs, DNS SANs, and the host part of email addresses.
         *
         * @param enforceHostnames whether to enforce hostnames
         * @return updated request
         */
        public Request enforceHostnames(boolean enforceHostnames) {
            return add("enforce_hostnames", enforceHostnames);
        }

        /**
         * Specifies if clients can request IP Subject Alternative Names. No authorization checking is performed except to verify
         * that the given values are valid IP addresses.
         *
         * @param allowIpSans whether to allow IP subject alternative names
         * @return updated request
         */
        public Request allowIpSans(boolean allowIpSans) {
            return add("allow_ip_sans", allowIpSans);
        }

        /**
         * Specifies if certificates are flagged for server use.
         * Defaults to {@code true}.
         *
         * @param serverFlag whether the certificates are flagged for server use
         * @return updated request
         */
        public Request serverFlag(boolean serverFlag) {
            return add("server_flag", serverFlag);
        }

        /**
         * Specifies if certificates are flagged for client use.
         * Defaults to {@code true}.
         *
         * @param clientFlag whether the certificates are flagged for server use
         * @return updated request
         */
        public Request clientFlag(boolean clientFlag) {
            return add("client_flag", clientFlag);
        }

        /**
         * Specifies if certificates are flagged for code signing use.
         * Defaults to {@code false}.
         *
         * @param codeSigningFlag whether the certificates are flagged for code signing use
         * @return updated request
         */
        public Request codeSigningFlag(boolean codeSigningFlag) {
            return add("code_signing_flag", codeSigningFlag);
        }

        /**
         * Specifies if certificates are flagged for email protection use.
         * Defaults to {@code false}.
         *
         * @param emailProtectionFlag whether the certificates are flagged for email protection use
         * @return updated request
         */
        public Request emailProtectionFlag(boolean emailProtectionFlag) {
            return add("email_protection_flag", emailProtectionFlag);
        }

        /**
         * Specifies the type of key to generate for generated private keys and the type of key expected for submitted CSRs.
         * Currently, rsa and ec are supported, or when signing CSRs any can be specified to allow keys of either type and with any
         * bit size (subject to &gt; 1024 bits for RSA keys).
         * <p>
         * Defaults to {@value PkiSecretsRx#KEY_TYPE_RSA}.
         *
         * @param keyType key type
         * @return updated request
         * @see PkiSecretsRx#KEY_TYPE_RSA
         * @see PkiSecretsRx#KEY_TYPE_EC
         */
        public Request keyType(String keyType) {
            return add("key_type", keyType);
        }

        /**
         * Specifies the number of bits to use for the generated keys.
         * This will need to be changed for {@code ec} keys, e.g., {@code 224}, {@code 256}, {@code 384} or {@code 521}.
         * <p>
         * Defaults to {@code 2048}.
         *
         * @param keyBits number of bits to use
         * @return updated request
         */
        public Request keyBits(int keyBits) {
            return add("key_bits", keyBits);
        }

        /**
         * Configure list of usages.
         *  Specifies the allowed key usage constraint on issued certificates. Valid values can be found at
         *  <a href="https://golang.org/pkg/crypto/x509/#KeyUsage">Key Usage</a>
         *  - simply drop the KeyUsage part of the value. Values are not case-sensitive. To specify
         *  no key usage constraints, set this to an empty list.
         * <p>
         * Defaults to {@code ["DigitalSignature", "KeyAgreement", "KeyEncipherment"]}.
         *
         * @param keyUsage list of usages
         * @return updated request
         */
        public Request keyUsage(List<String> keyUsage) {
            if (keyUsage.isEmpty()) {
                emptyArray("key_usage");
            } else {
                keyUsage.forEach(it -> addToArray("key_usage", it));
            }
            return this;
        }

        /**
         * When used with the CSR signing endpoint, the common name in the CSR will be used instead of taken from the JSON data.
         * This does not include any requested SANs in the CSR; use {@link #useCsrSans(boolean)} for that.
         * <p>
         * Defaults to {@code true}.
         *
         * @param useCsrCommonName whether to use the CSR common name
         * @return updated request
         */
        public Request useCsrCommonName(boolean useCsrCommonName) {
            return add("use_csr_common_name", useCsrCommonName);
        }

        /**
         * When used with the CSR signing endpoint, the subject alternate names in the CSR will be used instead of taken from the
         * JSON data. This does not include the common name in the CSR; use {@link #useCsrCommonName(boolean)} for that.
         * <p>
         * Defaults to {@code true}.
         *
         * @param useCsrSans whether to use the CSR subject alternative names
         * @return updated request
         */
        public Request useCsrSans(boolean useCsrSans) {
            return add("use_csr_sans", useCsrSans);
        }

        /**
         * Specifies the Serial Number, if any. Otherwise Vault will generate a random serial for you.
         * If you want more than one, specify alternative names using OID 2.5.4.5.
         *
         * @param serialNumber serial number to use
         * @return updated request
         */
        public Request serialNumber(String serialNumber) {
            return add("serial_number", serialNumber);
        }

        /**
         * Specifies if certificates issued/signed against this role will have Vault leases attached to them. Certificates can be
         * added to the CRL by vault revoke {@code lease_id} when certificates are associated with leases. It can also be done
         * using the pki/revoke endpoint. However, when lease generation is disabled, invoking pki/revoke would be the only way to
         * add the certificates to the CRL.
         * <p>
         * Defaults to {@code false}.
         *
         * @param generateLease whether Vault leases are attached to generated certificates
         * @return updated request
         */
        public Request generateLease(boolean generateLease) {
            return add("generate_lease", generateLease);
        }

        /**
         * If set, certificates issued/signed against this role will not be stored in the storage backend. This can improve
         * performance when issuing large numbers of certificates. However, certificates issued in this way cannot be enumerated or
         * revoked, so this option is recommended only for certificates that are non-sensitive, or extremely short-lived. This
         * option implies a value of false for {@link #generateLease(boolean)}.
         * <p>
         * Defaults to {@code false}.
         *
         * @param noStore if set to {@code true}, certificates are not stored in Vault
         * @return updated request
         */
        public Request noStore(boolean noStore) {
            return add("no_store", noStore);
        }

        /**
         * If set to false, makes the common name field optional while generating a certificate.
         * <p>
         * Defaults to {@code true}.
         *
         * @param requireCn set to {@code false} to make common name optional
         * @return updated request
         */
        public Request requireCn(boolean requireCn) {
            return add("require_cn", requireCn);
        }

        /**
         * Mark Basic Constraints valid when issuing non-CA certificates.
         *
         * @param basicConstraintsValidForNonCa defaults to {@code false}
         * @return updated builder
         */
        public Request basicConstraintsValidForNonCa(boolean basicConstraintsValidForNonCa) {
            return add("basic_constraints_valid_for_non_ca", basicConstraintsValidForNonCa);
        }

        /**
         * Specifies the duration by which to backdate the NotBefore property.
         *
         * @param notBeforeDuration duration
         * @return updated builder
         */
        public Request notBeforeDuration(Duration notBeforeDuration) {
            return add("not_before_duration", notBeforeDuration);
        }

        /**
         * Specifies the domains of the role. This is used with the {@link #allowBareDomains} and {@link #allowSubDomains} options.
         *
         * @param domain domain
         * @return updated request
         */
        public Request addAllowedDomain(String domain) {
            return addToArray("allowed_domains", domain);
        }

        /**
         * Defines allowed URI Subject Alternative Names. No authorization checking is performed except to verify that the given
         * values are valid URIs. This can be a comma-delimited list or a JSON string slice. Values can contain glob patterns (e.g.
         * spiffe://hostname/*).
         *
         * @param subjectAlternativeName san
         * @return updated request
         */
        public Request addAllowedUriSan(String subjectAlternativeName) {
            return addToArray("allowed_uri_sans", subjectAlternativeName);
        }

        /**
         * Defines allowed custom OID/UTF8-string SANs. This can be a comma-delimited list or a JSON string slice, where each
         * element has the same format as OpenSSL: {@code <oid>;<type>:<value>}, but the only valid type is UTF8 or UTF-8.
         * The value part of an element may be a {@code *} to allow any value with that OID.
         * Alternatively, specifying a single {@code *} will allow any other_sans input.
         *
         * @param subjectAlternativeName san
         * @return updated request
         */
        public Request addAllowedOtherSan(String subjectAlternativeName) {
            return addToArray("allowed_other_sans", subjectAlternativeName);
        }

        /**
         * Specifies the allowed extended key usage constraint on issued certificates. Valid values can be found at
         * <a href="https://golang.org/pkg/crypto/x509/#ExtKeyUsage">Ext Key usage</a>
         * - simply drop the ExtKeyUsage part of the value. Values are not case-sensitive.
         *
         * @param extKeyUsage key usage
         * @return updated request
         */
        public Request addExtKeyUsage(String extKeyUsage) {
            return addToArray("ext_key_usage", extKeyUsage);
        }

        /**
         * Add an extended usage OID.
         *
         * @param extKeyUsageOid OID of an ext key usage
         * @return updated request
         */
        public Request addExtKeyUsageOid(String extKeyUsageOid) {
            return addToArray("ext_key_usage_oids", extKeyUsageOid);
        }

        /**
         * Adds an OU (OrganizationalUnit) value in the subject field of issued certificates.
         *
         * @param orgUnit organization unit to add
         * @return updated request
         */
        public Request addOrgUnit(String orgUnit) {
            return addToArray("ou", orgUnit);
        }

        /**
         * Adds an O (Organization) value in the subject field of issued certificates.
         *
         * @param org organization unit to add
         * @return updated request
         */
        public Request addOrg(String org) {
            return addToArray("organization", org);
        }

        /**
         * Adds the C (Country) value in the subject field of issued certificates.
         *
         * @param country country to add
         * @return updated request
         */
        public Request addCountry(String country) {
            return addToArray("country", country);
        }

        /**
         * Adds the L (Locality) value in the subject field of issued certificates.
         *
         * @param locality locality to add
         * @return updated request
         */
        public Request addLocality(String locality) {
            return addToArray("locality", locality);
        }

        /**
         * Adds the ST (Province) values in the subject field of issued certificates.
         *
         * @param province province to add
         * @return updated request
         */
        public Request addProvince(String province) {
            return addToArray("province", province);
        }

        /**
         * Adds a Street Address values in the subject field of issued certificates.
         *
         * @param streetAddress street address to add
         * @return updated request
         */
        public Request addStreetAddress(String streetAddress) {
            return addToArray("street_address", streetAddress);
        }

        /**
         * Adds a Postal Code values in the subject field of issued certificates.
         *
         * @param postalCode postalCode address to add
         * @return updated request
         */
        public Request addPostalCode(String postalCode) {
            return addToArray("postal_code", postalCode);
        }

        /**
         * Add a policy identifier OID.
         *
         * @param policyIdentifierOid policy identifier OID
         * @return updated request
         */
        public Request addPolicyIdentifier(String policyIdentifierOid) {
            return addToArray("policy_identifiers", policyIdentifierOid);
        }

        /**
         * Configure role name.
         *
         * @param roleName name of the role
         * @return updated request
         */
        public Request roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        String roleName() {
            if (roleName == null) {
                throw new VaultApiException("PkiRole.Request role must be defined");
            }
            return roleName;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiResponse {
        private Response(Builder builder) {
            super(builder);
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder extends ApiResponse.Builder<Builder, Response> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
