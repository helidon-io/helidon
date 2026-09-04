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

lists=/var/lib/apt/lists

verify() {
    expected=$1
    file=$2
    actual=$(sha256sum "${file}")
    actual=${actual%% *}
    if [ "${actual}" != "${expected}" ]; then
        echo >&2 "Unexpected Debian snapshot metadata checksum: ${file}"
        exit 1
    fi
}

verify \
    77737fa4b34f2693e982cc9ee35736816c35a7778fc2d326cc1bbf5b301fe1aa \
    "${lists}/snapshot.debian.org_archive_debian_20260715T000000Z_dists_bookworm_InRelease"
verify \
    14d176f482a9eae4c434ca399fe95be1cbdd3362619f6db4b7fc0670acf74e33 \
    "${lists}/snapshot.debian.org_archive_debian_20260715T000000Z_dists_bookworm-updates_InRelease"
verify \
    ab202b2a721d0566da4e6976fc53ddcfb6ddf62c1d7d6689605240830e479e7c \
    "${lists}/snapshot.debian.org_archive_debian-security_20260715T000000Z_dists_bookworm-security_InRelease"
