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
import pathlib


RUNNER_ROOT = pathlib.Path("/opt/quic-interop-runner")
QUIC_GO_IMAGE = (
    "martenseemann/quic-go-interop"
    "@sha256:93123a8a0315ede4e35feb85a296337e674846cb853a2f1b320d75b9ef617519"
)
SIMULATOR_IMAGE = (
    "martenseemann/quic-network-simulator"
    "@sha256:c23d82a55caffe681b1bdae65d4d30d23e1283141a414a7f02ee56cf15f9c6b9"
)


implementations_path = RUNNER_ROOT / "implementations_quic.json"
implementations = json.loads(implementations_path.read_text(encoding="utf-8"))
if implementations.get("quic-go", {}).get("image") != "martenseemann/quic-go-interop:latest":
    raise ValueError("Upstream runner no longer declares the expected quic-go image")
if "helidon-http3" in implementations:
    raise ValueError("Upstream runner already declares a helidon-http3 implementation")
implementations["quic-go"]["image"] = QUIC_GO_IMAGE
implementations["helidon-http3"] = {
    "image": "helidon-http3:placeholder",
    "url": "https://github.com/helidon-io/helidon",
    "role": "server",
}
implementations_path.write_text(json.dumps(implementations, indent=2) + "\n", encoding="utf-8")

compose_path = RUNNER_ROOT / "docker-compose.yml"
compose = compose_path.read_text(encoding="utf-8")
simulator_image = "image: martenseemann/quic-network-simulator"
if compose.count(simulator_image) != 1:
    raise ValueError("Upstream runner compose file no longer declares exactly one expected simulator image")
compose = compose.replace(simulator_image, "image: " + SIMULATOR_IMAGE, 1)
compose = "\n".join(line for line in compose.splitlines() if "interface_name:" not in line) + "\n"
compose_path.write_text(compose, encoding="utf-8")

testcase_path = RUNNER_ROOT / "testcase.py"
testcase = testcase_path.read_text(encoding="utf-8")
cleanup_start = testcase.index("def docker_cleanup_dir(directory: str):")
cleanup_end = testcase.index("\n\nclass Perspective", cleanup_start)
cleanup = '''def docker_cleanup_dir(directory: str):
    """Remove root-owned files directly from the isolated runner tool."""
    try:
        for entry in os.scandir(directory):
            if entry.is_dir(follow_symlinks=False):
                shutil.rmtree(entry.path)
            else:
                os.unlink(entry.path)
    except Exception as e:
        logging.debug("Direct cleanup of %s failed: %s", directory, e)
'''
testcase = testcase[:cleanup_start] + cleanup + testcase[cleanup_end:]
testcase_path.write_text(testcase, encoding="utf-8")
