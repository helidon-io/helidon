#!/bin/sh -e
#
# Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

readonly OCI_SECRET_FILE=$(dirname ${0})/oci-env

if [ ! -f ${OCI_SECRET_FILE} ] ; then
    if [ -f ${HOME}/.oci/config ] ; then
        echo "Found ${HOME}/.oci/config"
        eval $(cat ~/.oci/config | sed -n '/\[ADMIN_USER\]/q;p' | sed '1d' | sed -E s@'^(.*)=(.*)$'@'oci_\1=\2'@g)
    fi

    if [ -z "${oci_user}" ] ; then
        read -p "User OCID: " oci_user
    fi
    if [ -z "${oci_user}" ] ; then
        echo "ERROR: user OCID is empty"
    fi

    if [ -z "${oci_key_file}" ] ; then
        read -p "Private key location: " oci_key_file
    fi
    if [ -z "${oci_key_file}" ] || [ ! -f ${oci_key_file} ] ; then
        echo "ERROR: Private key is not a valid file"
    fi

    if [ -z "${oci_pass_phrase}" ] ; then
        read -p "Private key passphrase: " oci_pass_phrase
    fi

    if [ -z "${oci_fingerprint}" ] ; then
        read -p "Public key fingerprint: " oci_fingerprint
    fi
    if [ -z "${oci_fingerprint}" ] ; then
        echo "ERROR: Public key is empty"
    fi

    if [ -z "${oci_tenancy}" ] ; then
        read -p "Tenancy OCID: " oci_tenancy
    fi
    if [ -z "${oci_tenancy}" ] ; then
        echo "ERROR: Tenancy OCID is empty"
    fi

    if [ -z "${oci_region}" ] ; then
        read -p "Region: " oci_region
    fi
    if [ -z "${oci_region}" ] ; then
        echo "ERROR: Region is empty"
    fi

    if [ -z "${oci_compartment}" ] ; then
      read -p "Compartment OCID: " oci_compartment
    fi
    if [ -z "${oci_compartment}" ] ; then
        echo "ERROR: Compartment OCID is empty"
    fi

    readonly OCI_AUTH_PRIVATEKEY="$(cat ~/.oci/oci_api_key.pem)"
    readonly OCI_AUTH_FINGERPRINT="${oci_fingerprint}"
    readonly OCI_AUTH_PASSPHRASE="${oci_passphrase}"
    readonly OCI_AUTH_TENANCY="${oci_tenancy}"
    readonly OCI_AUTH_USER="${oci_user}"
    readonly OCI_LISTREGIONS_COMPARTMENT="${oci_compartment}"
    readonly OCI_LISTREGIONS_COMPARTMENT_REGION="${oci_region}"

    echo "export OCI_AUTH_PRIVATEKEY=\"${OCI_AUTH_PRIVATEKEY}\"" > ${OCI_SECRET_FILE}
    echo "export OCI_AUTH_FINGERPRINT=\"${OCI_AUTH_FINGERPRINT}\"" >> ${OCI_SECRET_FILE}
    echo "export OCI_AUTH_PASSPHRASE=\"${OCI_AUTH_PASSPHRASE}\"" >> ${OCI_SECRET_FILE}
    echo "export OCI_AUTH_TENANCY=\"${OCI_AUTH_TENANCY}\"" >> ${OCI_SECRET_FILE}
    echo "export OCI_AUTH_USER=\"${OCI_AUTH_USER}\"" >> ${OCI_SECRET_FILE}
    echo "export OCI_LISTREGIONS_COMPARTMENT=\"${OCI_LISTREGIONS_COMPARTMENT}\"" >> ${OCI_SECRET_FILE}
    echo "export OCI_LISTREGIONS_COMPARTMENT_REGION=\"${OCI_LISTREGIONS_COMPARTMENT_REGION}\"" >> ${OCI_SECRET_FILE}
fi

if [ "${1}" = "-k8s" ] ; then
    kubectl create secret generic oci-objectstorage-secret \
    --from-literal=OCI_AUTH_FINGERPRINT="${OCI_AUTH_FINGERPRINT}" \
    --from-literal=OCI_AUTH_PASSPHRASE="${OCI_AUTH_PASSPHRASE}" \
    --from-literal=OCI_AUTH_PRIVATEKEY="${OCI_AUTH_PRIVATEKEY}" \
    --from-literal=OCI_AUTH_TENANCY="${OCI_AUTH_TENANCY}" \
    --from-literal=OCI_AUTH_USER="${OCI_AUTH_USER}" \
    --from-literal=OCI_LISTREGIONS_COMPARTMENT="${OCI_LISTREGIONS_COMPARTMENT}" \
    --from-literal=OCI_LISTREGIONS_COMPARTMENT_REGION="${OCI_LISTREGIONS_COMPARTMENT_REGION}"
fi