#!/bin/sh
#
# Copyright (c) 2026 Oracle and/or its affiliates.
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
#

set -eu

mode=${1:?Mode is required}
requirements=${2:?Requirements file is required}
lock_directory=${3:?Lock directory is required}
host_architecture=$(apk --print-arch)
architecture=${4:-${host_architecture}}

case "${mode}" in
    generate|verify-install)
        ;;
    *)
        echo >&2 "Unsupported runner-tool APK lock mode: ${mode}"
        exit 1
        ;;
esac

case "${architecture}" in
    aarch64|x86_64)
        ;;
    *)
        echo >&2 "Unsupported runner-tool architecture: ${architecture}"
        exit 1
        ;;
esac

download_directory=$(mktemp -d)
trap 'rm -rf "${download_directory}"' EXIT

set --
while IFS= read -r requirement || [ -n "${requirement}" ]; do
    case "${requirement}" in
        ""|\#*)
            continue
            ;;
    esac
    package_name=${requirement%%=*}
    if [ "${package_name}" = "${requirement}" ] || [ -z "${package_name}" ]; then
        echo >&2 "Runner-tool APK requirement must pin an exact version: ${requirement}"
        exit 1
    fi
    set -- "$@" "${requirement}"
done < "${requirements}"

if [ "$#" -eq 0 ]; then
    echo >&2 "Runner-tool APK requirements are empty"
    exit 1
fi

if [ "${architecture}" != "${host_architecture}" ]; then
    echo >&2 "Runner-tool APK installation architecture does not match the image: ${architecture}"
    exit 1
fi

apk add --no-cache --simulate "$@"

set --
while IFS= read -r requirement || [ -n "${requirement}" ]; do
    case "${requirement}" in
        ""|\#*)
            continue
            ;;
    esac
    set -- "$@" "${requirement%%=*}"
done < "${requirements}"

apk fetch --no-cache --recursive --output "${download_directory}" "$@"

while IFS= read -r requirement || [ -n "${requirement}" ]; do
    case "${requirement}" in
        ""|\#*)
            continue
            ;;
    esac
    package_name=${requirement%%=*}
    package_version=${requirement#*=}
    if [ ! -f "${download_directory}/${package_name}-${package_version}.apk" ]; then
        echo >&2 "Runner-tool APK root revision was not resolved: ${requirement}"
        exit 1
    fi
done < "${requirements}"

lock="${lock_directory}/apk.runner-tool.${architecture}.lock"

if [ "${mode}" = "generate" ]; then
    for package in "${download_directory}"/*.apk; do
        apk verify "${package}"
    done

    {
        echo "#"
        echo "# Copyright (c) 2026 Oracle and/or its affiliates."
        echo "#"
        echo "# Licensed under the Apache License, Version 2.0 (the \"License\");"
        echo "# you may not use this file except in compliance with the License."
        echo "# You may obtain a copy of the License at"
        echo "#"
        echo "#     http://www.apache.org/licenses/LICENSE-2.0"
        echo "#"
        echo "# Unless required by applicable law or agreed to in writing, software"
        echo "# distributed under the License is distributed on an \"AS IS\" BASIS,"
        echo "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied."
        echo "# See the License for the specific language governing permissions and"
        echo "# limitations under the License."
        echo "#"
        echo
        for package in "${download_directory}"/*.apk; do
            package_name=${package##*/}
            checksum=$(sha256sum "${package}")
            checksum=${checksum%% *}
            printf '%s  ./%s\n' "${checksum}" "${package_name}"
        done
    } > "${lock}"
    exit 0
fi

if [ ! -f "${lock}" ]; then
    echo >&2 "Runner-tool APK lock is missing: ${lock}"
    exit 1
fi

expected_count=0
while read -r expected_hash expected_name extra; do
    case "${expected_hash}" in
        ""|\#*)
            continue
            ;;
    esac
    if [ -n "${extra:-}" ]; then
        echo >&2 "Malformed runner-tool APK lock entry: ${expected_hash} ${expected_name} ${extra}"
        exit 1
    fi
    case "${expected_name}" in
        ./*.apk)
            ;;
        *)
            echo >&2 "Unsafe runner-tool APK lock path: ${expected_name}"
            exit 1
            ;;
    esac
    package="${download_directory}/${expected_name#./}"
    if [ ! -f "${package}" ]; then
        echo >&2 "Locked runner-tool APK was not resolved: ${expected_name}"
        exit 1
    fi
    actual_hash=$(sha256sum "${package}")
    actual_hash=${actual_hash%% *}
    if [ "${actual_hash}" != "${expected_hash}" ]; then
        echo >&2 "Runner-tool APK checksum mismatch: ${expected_name}"
        exit 1
    fi
    expected_count=$((expected_count + 1))
done < "${lock}"

actual_count=0
for package in "${download_directory}"/*.apk; do
    package_name="./${package##*/}"
    matches=0
    while read -r expected_hash expected_name extra; do
        case "${expected_hash}" in
            ""|\#*)
                continue
                ;;
        esac
        if [ "${expected_name}" = "${package_name}" ]; then
            matches=$((matches + 1))
        fi
    done < "${lock}"
    if [ "${matches}" -ne 1 ]; then
        echo >&2 "Unlocked or duplicated runner-tool APK: ${package_name}"
        exit 1
    fi
    actual_count=$((actual_count + 1))
done

if [ "${actual_count}" -ne "${expected_count}" ]; then
    echo >&2 "Runner-tool APK lock count mismatch: expected ${expected_count}, resolved ${actual_count}"
    exit 1
fi

for package in "${download_directory}"/*.apk; do
    apk verify "${package}"
done

apk add --no-cache --no-network "${download_directory}"/*.apk
