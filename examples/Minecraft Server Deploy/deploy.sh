#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration — adjust these to match your environment
# ---------------------------------------------------------------------------
INSTANCE="MyInstance01"
REPOSILITE_BASE_URL="https://maven.example.com/"
MODS_DIR="/home/amp/.ampdata/instances/${INSTANCE}/Minecraft/mods"
LOG_FILE="/home/amp/deploy.log"
# ---------------------------------------------------------------------------

ARTIFACT_PATH="$1"
FILENAME=$(basename "$ARTIFACT_PATH")

# Derive the artifact prefix by stripping the version suffix so old jars can
# be found regardless of their version. Assumes Maven naming: name-1.2.3.jar
ARTIFACT_PREFIX=$(echo "$FILENAME" | sed 's/-[0-9].*$//')

# Fork the heavy work to the background so the webhook call returns 200
# immediately instead of timing out during the server restart.
{
    echo "[$(date -Iseconds)] Deploying $FILENAME to ${INSTANCE}"

    echo "[$(date -Iseconds)] Stopping server..."
    ampinstmgr -q ${INSTANCE}

    echo "[$(date -Iseconds)] Removing old jars matching ${ARTIFACT_PREFIX}-*.jar..."
    find "$MODS_DIR" -maxdepth 1 -name "${ARTIFACT_PREFIX}-*.jar" -delete

    echo "[$(date -Iseconds)] Downloading ${REPOSILITE_BASE_URL}${ARTIFACT_PATH}..."
    curl -fsSL "${REPOSILITE_BASE_URL}${ARTIFACT_PATH}" -o "${MODS_DIR}/${FILENAME}"

    echo "[$(date -Iseconds)] Starting server..."
    ampinstmgr -s ${INSTANCE}

    echo "[$(date -Iseconds)] Done."
} >> "$LOG_FILE" 2>&1 &

exit 0
