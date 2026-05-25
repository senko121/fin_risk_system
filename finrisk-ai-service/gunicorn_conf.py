"""
Gunicorn configuration for finrisk-ai-service (Linux / WSL / Docker).
Gunicorn is NOT supported on Windows — use start.bat on Windows dev machines.

Worker model
────────────
  UvicornWorker: each gunicorn worker runs its own asyncio event loop.
  The event loop handles HTTP I/O; heavy inference is dispatched to the
  per-worker ThreadPoolExecutor (see INFERENCE_WORKERS in main.py).

preload_app = False
  Each worker imports main.py independently after fork.
  Required because main.py creates a ThreadPoolExecutor at module level.
  Threads do not survive fork(), so preload_app=True would leave every
  worker with a broken executor.  preload_app=False avoids this entirely.

Worker count sizing (CPU inference, no GPU)
──────────────────────────────────────────
  Effective concurrency = GUNICORN_WORKERS × INFERENCE_WORKERS threads.
  Keep the product ≤ physical CPU count to avoid over-subscription.

  4-core host (face + emotion on same machine):
    GUNICORN_WORKERS=1, INFERENCE_WORKERS=2  →  2 threads total per service

  4-core host (dedicated to face service only):
    GUNICORN_WORKERS=2, INFERENCE_WORKERS=2  →  4 threads

  8-core host (dedicated):
    GUNICORN_WORKERS=2, INFERENCE_WORKERS=4  →  8 threads

  Override at runtime:
    GUNICORN_WORKERS=2 INFERENCE_WORKERS=2 ./start.sh
"""
import os

# ── Binding ───────────────────────────────────────────────────────────────────
bind = "0.0.0.0:5000"

# ── Worker class ──────────────────────────────────────────────────────────────
worker_class = "uvicorn.workers.UvicornWorker"

# ── Worker count ──────────────────────────────────────────────────────────────
workers = int(os.environ.get("GUNICORN_WORKERS", "2"))

# ── Preload ───────────────────────────────────────────────────────────────────
preload_app = False

# ── Timeouts ──────────────────────────────────────────────────────────────────
# ArcFace/MTCNN model loading on first warm-up can take 30-90 s on slow storage.
# timeout covers the full worker boot + warm-up window.
timeout         = 120
graceful_timeout = 30
keepalive        = 5

# ── Worker cycling (TF memory guard) ──────────────────────────────────────────
# TensorFlow accumulates memory fragmentation over many requests.
# Recycling workers periodically keeps RSS stable.
max_requests        = 200
max_requests_jitter = 40   # randomised to avoid simultaneous restart thundering-herd

# ── Logging ───────────────────────────────────────────────────────────────────
accesslog = "-"    # stdout — captured by systemd / docker logs
errorlog  = "-"    # stderr
loglevel  = "info"

# ── Worker lifecycle hooks ────────────────────────────────────────────────────
def when_ready(server):
    server.log.info(
        f"[face-service] Gunicorn master ready  "
        f"workers={workers}  bind={bind}"
    )

def post_fork(server, worker):
    server.log.info(
        f"[face-service] Worker spawned  pid={worker.pid}  "
        f"age={worker.age}"
    )

def worker_int(worker):
    worker.log.info(
        f"[face-service] Worker interrupted (SIGINT)  pid={worker.pid}"
    )

def worker_abort(worker):
    worker.log.info(
        f"[face-service] Worker aborted (SIGABRT)  pid={worker.pid}"
    )

def worker_exit(server, worker):
    server.log.info(
        f"[face-service] Worker exited  pid={worker.pid}  "
        f"requests_handled={getattr(worker, 'nr', '?')}"
    )

def on_exit(server):
    server.log.info("[face-service] Gunicorn master shutdown complete")
