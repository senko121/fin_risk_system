#!/usr/bin/env bash
# start.sh — finrisk-ai-service (Linux / WSL / Docker)
# For Windows development use start.bat instead.
set -euo pipefail

SERVICE_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SERVICE_DIR"

# ── Tuning (override via env) ─────────────────────────────────────────────────
export GUNICORN_WORKERS="${GUNICORN_WORKERS:-2}"
export INFERENCE_WORKERS="${INFERENCE_WORKERS:-2}"

# TF internal thread pools — limit to avoid over-subscription when running
# multiple workers.  Each worker gets (INTRAOP + INTEROP) TF threads on top
# of INFERENCE_WORKERS Python threads.
export TF_NUM_INTRAOP_THREADS="${TF_NUM_INTRAOP_THREADS:-2}"
export TF_NUM_INTEROP_THREADS="${TF_NUM_INTEROP_THREADS:-1}"

echo "[face-service] Activating virtualenv ..."
# shellcheck disable=SC1091
source venv/bin/activate

echo "[face-service] Starting gunicorn"
echo "  workers          = $GUNICORN_WORKERS"
echo "  inference_threads= $INFERENCE_WORKERS"
echo "  tf_intraop       = $TF_NUM_INTRAOP_THREADS"
echo "  tf_interop       = $TF_NUM_INTEROP_THREADS"
echo "  port             = 5000"

exec gunicorn main:app \
    --config gunicorn_conf.py \
    --pid /tmp/finrisk-face-service.pid
