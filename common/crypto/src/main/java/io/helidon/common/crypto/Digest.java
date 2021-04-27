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

package io.helidon.common.crypto;

import java.util.Arrays;
import java.util.regex.Matcher;

import io.helidon.integrations.common.rest.Base64Value;

import static io.helidon.common.crypto.CryptoCommonConstants.PREFIX;
import static io.helidon.common.crypto.CryptoCommonConstants.PREFIX_PATTERN;

/**
 * Common digest which simplifies digest creation and its verification.
 */
public interface Digest {

    /**
     * Create digest of the value.
     *
     * @param value value to make digest from
     * @return digest of the value
     */
    Base64Value digest(Base64Value value);

    /**
     * Verify the digest of the value against the provided digest.
     *
     * @param toVerify value to create digest from
     * @param digestToVerify digest which needs to be verified
     * @return whether both digests are the same
     */
    default boolean verify(Base64Value toVerify, Base64Value digestToVerify) {
        return Arrays.equals(digest(toVerify).toBytes(), digestToVerify.toBytes());
    }

    /**
     * Create digest of the value and return as String format.
     * <p>
     * Template format: <code>helidon:(formatVersion):digestInBase64</code><p>
     * Example: <code>helidon:2:digestInBase64</code>
     *
     * @param value value to make digest from
     * @return String representation of the value digest
     */
    default String digestString(Base64Value value) {
        return PREFIX + digest(value).toBase64();
    }

    /**
     * Verify the digest of the value against the provided digest in String format.
     * <p>
     * Template format: <code>helidon:(formatVersion):digestInBase64</code><p>
     * Example: <code>helidon:2:digestInBase64</code>
     *
     * @param toVerify value to create digest from
     * @param digestToVerify digest in String format which needs to be verified
     * @return whether both digests are the same
     */
    default boolean verifyString(Base64Value toVerify, String digestToVerify) {
        Matcher matcher = PREFIX_PATTERN.matcher(digestToVerify);
        if (matcher.matches()) {
            return verify(toVerify, Base64Value.createFromEncoded(matcher.group(2)));
        } else {
            throw new CryptoException("String does not contain Helidon prefix: " + digestToVerify);
        }
    }

}
