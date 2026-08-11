#!/usr/bin/env bash
#
# Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

set -euo pipefail

readonly BASE_URL="https://github.com/koalaman/shellcheck/releases/download"
readonly VERSION=0.11.0
readonly CACHE_DIR="${HOME}/.shellcheck"

mkdir -p "${CACHE_DIR}"
if [[ ! -x "${CACHE_DIR}/${VERSION}/shellcheck" ]]; then
  arch=$(uname -m | tr '[:upper:]' '[:lower:]')
  platform=$(uname -s | tr '[:upper:]' '[:lower:]')
  if [[ ${arch} == arm64 ]]; then
    arch=aarch64
  fi
  curl -fsSL -o "${CACHE_DIR}/sc.tar.xz" \
    "${BASE_URL}/v${VERSION}/shellcheck-v${VERSION}.${platform}.${arch}.tar.xz"
  tar -xf "${CACHE_DIR}/sc.tar.xz" -C "${CACHE_DIR}"
  mkdir -p "${CACHE_DIR}/${VERSION}"
  mv "${CACHE_DIR}/shellcheck-v${VERSION}/shellcheck" "${CACHE_DIR}/${VERSION}/shellcheck"
  rm -rf "${CACHE_DIR}/shellcheck-v${VERSION}" "${CACHE_DIR}/sc.tar.xz"
fi
export PATH="${CACHE_DIR}/${VERSION}:${PATH}"

echo "ShellCheck version"
shellcheck --version

status_code=0
while IFS= read -r -d '' file; do
  [[ -f ${file} ]] || continue
  printf '\n-- Checking file: %s --\n' "${file}"
  shellcheck "${file}" || status_code=$?
done < <(git ls-files -z -- '*.sh')

exit "${status_code}"
