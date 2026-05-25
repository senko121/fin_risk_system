@echo off
:: start.bat — finrisk-ai-service (Windows development)
:: Gunicorn is NOT available on Windows (requires POSIX fcntl).
:: This script uses uvicorn --workers which spawns processes via Python
:: multiprocessing with the "spawn" start method.
::
:: NOTE: True process-level TF isolation requires Linux + start.sh.
:: On Windows, each spawned worker re-imports main.py (spawn strategy),
:: which loads the model independently.  If --workers causes issues on
:: your Python/TF version, change UVICORN_WORKERS to 1 (single-worker
:: mode still benefits from the ThreadPoolExecutor added in Issue #11).
setlocal

cd /d "%~dp0"

if not defined UVICORN_WORKERS   set UVICORN_WORKERS=2
if not defined INFERENCE_WORKERS set INFERENCE_WORKERS=2

set TF_NUM_INTRAOP_THREADS=2
set TF_NUM_INTEROP_THREADS=1

echo [face-service] Starting on Windows
echo   workers           = %UVICORN_WORKERS%
echo   inference_threads = %INFERENCE_WORKERS%
echo   port              = 5000

call venv\Scripts\activate.bat

python -m uvicorn main:app ^
    --host 0.0.0.0 ^
    --port 5000 ^
    --workers %UVICORN_WORKERS%
