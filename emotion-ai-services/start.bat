@echo off
:: start.bat — emotion-ai-services (Windows development)
:: Gunicorn is NOT available on Windows (requires POSIX fcntl).
:: See start.sh for Linux / WSL / Docker deployment.
setlocal

cd /d "%~dp0"

if not defined UVICORN_WORKERS          set UVICORN_WORKERS=2
if not defined EMOTION_INFERENCE_WORKERS set EMOTION_INFERENCE_WORKERS=2

set TF_NUM_INTRAOP_THREADS=2
set TF_NUM_INTEROP_THREADS=1

echo [emotion-service] Starting on Windows
echo   workers           = %UVICORN_WORKERS%
echo   inference_threads = %EMOTION_INFERENCE_WORKERS%
echo   port              = 5001

call venv_emotion\Scripts\activate.bat

python -m uvicorn main:app ^
    --host 0.0.0.0 ^
    --port 5001 ^
    --workers %UVICORN_WORKERS%
