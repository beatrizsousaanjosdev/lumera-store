#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

# macOS: discover the API socket of the running Podman machine.
# Respect an existing DOCKER_HOST (also useful for Linux and CI).
if [ -z "${DOCKER_HOST:-}" ]; then
    if command -v podman >/dev/null 2>&1; then
        podman_command=podman
    elif [ -x /opt/podman/bin/podman ]; then
        podman_command=/opt/podman/bin/podman
    else
        echo 'Podman was not found. Add it to PATH.' >&2
        exit 1
    fi
    podman_socket=$("$podman_command" machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
    if [ ! -S "$podman_socket" ]; then
        echo 'Podman socket is unavailable. Start the Podman machine first.' >&2
        exit 1
    fi
    export DOCKER_HOST="unix://$podman_socket"
fi

# Rootless Podman: containers are stopped by the JUnit Testcontainers lifecycle.
export TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-true}"
exec ./gradlew test "$@"
