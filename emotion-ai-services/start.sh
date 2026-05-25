#!/usr/bin/env bash
# start.sh — emotion-ai-services (Linux / WSL / Docker)
# For Windows development use start.bat instead.
set -euo pipefail

SERVICE_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SERVICE_DIR"

# ── Tuning (override via env) ─────────────────────────────────────────────────
export GUNICORN_WORKERS="${GUNICORN_WORKERS:-2}"
export EMOTION_INFERENCE_WORKERS="${EMOTION_INFERENCE_WORKERS:-2}"

export TF_NUM_INTRAOP_THREADS="${TF_NUM_INTRAOP_THREADS:-2}"
export TF_NUM_INTEROP_THREADS="${TF_NUM_INTEROP_THREADS:-1}"

echo "[emotion-service] Activating virtualenv ..."
# shellcheck disable=SC1091
source venv_emotion/bin/activate

echo "[emotion-service] Starting gunicorn"
echo "  workers          = $GUNICORN_WORKERS"
echo "  inference_threads= $EMOTION_INFERENCE_WORKERS"
echo "  tf_intraop       = $TF_NUM_INTRAOP_THREADS"
echo "  tf_interop       = $TF_NUM_INTEROP_THREADS"
echo "  port             = 5001"

exec gunicorn main:app \
    --config gunicorn_conf.py \
    --pid /tmp/finrisk-emotion-service.pid
