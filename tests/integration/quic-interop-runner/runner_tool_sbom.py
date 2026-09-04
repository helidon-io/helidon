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

import json
import sys
from pathlib import Path


if len(sys.argv) != 3:
    raise SystemExit("Usage: runner_tool_sbom.py <SPDX input> <canonical output>")

source = Path(sys.argv[1])
target = Path(sys.argv[2])
document = json.loads(source.read_text(encoding="utf-8"))
if document.get("spdxVersion") != "SPDX-2.3":
    raise SystemExit("Runner-tool SBOM must use SPDX 2.3")

identities = set()
for package in document.get("packages", []):
    name = package.get("name")
    if not isinstance(name, str) or not name:
        raise SystemExit("Runner-tool SBOM package has no name")
    version = package.get("versionInfo", "")
    if not isinstance(version, str):
        raise SystemExit(f"Runner-tool SBOM package has an invalid version: {name}")
    purls = set()
    for reference in package.get("externalRefs", []):
        if reference.get("referenceType") != "purl":
            continue
        locator = reference.get("referenceLocator")
        if not isinstance(locator, str) or not locator.startswith("pkg:"):
            raise SystemExit(f"Runner-tool SBOM package has an invalid PURL: {name}")
        locator_without_fragment, separator, fragment = locator.partition("#")
        purl, query_separator, query = locator_without_fragment.partition("?")
        if query_separator:
            qualifiers = sorted(part for part in query.split("&") if not part.startswith("arch="))
            if qualifiers:
                purl += "?" + "&".join(qualifiers)
        if separator:
            purl += "#" + fragment
        purls.add(purl)
    identities.add((name, version, tuple(sorted(purls))))

canonical = {
    "formatVersion": 1,
    "packages": [
        {
            "name": name,
            "purls": list(purls),
            "version": version,
        }
        for name, version, purls in sorted(identities)
    ],
}
target.write_text(json.dumps(canonical, indent=2, sort_keys=True) + "\n", encoding="utf-8")
