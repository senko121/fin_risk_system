"""
Gunicorn configuration for emotion-ai-services (Linux / WSL / Docker).
Gunicorn is NOT supported on Windows — use start.bat on Windows dev machines.

Worker model
────────────
  UvicornWorker: each gunicorn worker runs its own asyncio event loop.
  The event loop handles HTTP I/O; model.predict() is dispatched to the
  per-worker ThreadPoolExecutor (see EMOTION_INFERENCE_WORKERS in main.py).

preload_app = False
  Each worker imports main.py independently after fork.
  Required: main.py loads the Keras model at module level AND creates a
  ThreadPoolExecutor at module level.  Threads do not survive fork(), so
  preload_app=True would leave every worker with a broken executor.

Worker count sizing (CPU inference, no GPU)
──────────────────────────────────────────
  Effective concurrency = GUNICORN_WORKERS × EMOTION_INFERENCE_WORKERS threads.
  Keep the product ≤ physical CPU count to avoid over-subscription.

  4-core host (face + emotion on same machine):
    GUNICORN_WORKERS=1, EMOTION_INFERENCE_WORKERS=2  →  2 threads per service

  4-core host (dedicated to emotion service only):
    GUNICORN_WORKERS=2, EMOTION_INFERENCE_WORKERS=2  →  4 threads

  8-core host (dedicated):
    GUNICORN_WORKERS=2, EMOTION_INFERENCE_WORKERS=4  →  8 threads

  Override at runtime:
    GUNICORN_WORKERS=2 EMOTION_INFERENCE_WORKERS=2 ./start.sh
"""
import os

# ── Binding ───────────────────────────────────────────────────────────────────
bind = "0.0.0.0:5001"

# ── Worker class ──────────────────────────────────────────────────────────────
worker_class = "uvicorn.workers.UvicornWorker"

# ── Worker count ──────────────────────────────────────────────────────────────
workers = int(os.environ.get("GUNICORN_WORKERS", "2"))

# ── Preload ───────────────────────────────────────────────────────────────────
preload_app = False

# ── Timeouts ──────────────────────────────────────────────────────────────────
# Keras model warm-up is fast (~1 s) but TF XLA compilation on first batch
# can add several seconds.  60 s is a conservative safe ceiling.
timeout          = 60
graceful_timeout = 30
keepalive        = 5

# ── Worker cycling (TF memory guard) ──────────────────────────────────────────
max_requests        = 500    # emotion model is lighter; can handle more before recycle
max_requests_jitter = 100

# ── Logging ───────────────────────────────────────────────────────────────────
accesslog = "-"
errorlog  = "-"
loglevel  = "info"

# ── Worker lifecycle hooks ────────────────────────────────────────────────────
def when_ready(server):
    server.log.info(
        f"[emotion-service] Gunicorn master ready  "
        f"workers={workers}  bind={bind}"
    )

def post_fork(server, worker):
    server.log.info(
        f"[emotion-service] Worker spawned  pid={worker.pid}  "
        f"age={worker.age}"
    )

def worker_int(worker):
    worker.log.info(
        f"[emotion-service] Worker interrupted (SIGINT)  pid={worker.pid}"
    )

def worker_abort(worker):
    worker.log.info(
        f"[emotion-service] Worker aborted (SIGABRT)  pid={worker.pid}"
    )

def worker_exit(server, worker):
    server.log.info(
        f"[emotion-service] Worker exited  pid={worker.pid}  "
        f"requests_handled={getattr(worker, 'nr', '?')}"
    )

def on_exit(server):
    server.log.info("[emotion-service] Gunicorn master shutdown complete")
