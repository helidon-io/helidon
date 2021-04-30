#!/bin/bash
#
# Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

###############################################################################
# Error handling functions                                                    #
###############################################################################

# Multiple definition protection.
# The same code is included in both local and pipeline environment setup.
if [ -z "${__ERROR_HANDLER_INCLUDED__}" ]; then
    readonly __ERROR_HANDLER_INCLUDED__='true'

    # Default error handler.
    # Shell variables: CODE
    #                  BASH_SOURCE
    #                  LINENO
    #                  BASH_COMMAND
    on_error() {
        CODE="${?}" && \
        set +x && \
        printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
            "${CODE}" "${BASH_SOURCE}" "${LINENO}" "${BASH_COMMAND}"
    }

    # Error handling setup
    # Arguments: $1 - error handler name (optional, default name is 'on_error')
    error_trap_setup() {
        # trace ERR through pipes
        set -o pipefail || true
        # trace ERR through commands and functions
        set -o errtrace || true
        # exit the script if any statement returns a non-true return value
        set -o errexit || true
        # Set error handler
        trap "${1:-on_error}" ERR
    }

else
    echo "WARNING: ${WS_DIR}/etc/scripts/includes/error_handlers.sh included multiple times."
    echo "WARNING: Make sure that only one from local and pipeline environment setups is loaded."
fi
