"""InsightFace buffalo_l singleton — face detection + ArcFace embedding.

Loaded once per process during warmup; reused across all requests.
Swapping the recognition model (e.g. AdaFace, CurricularFace) only
requires changing the FaceEngine initialiser — no caller changes needed.
"""
from __future__ import annotations

import logging
from typing import Optional

import numpy as np
from insightface.app import FaceAnalysis

_log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Module-level singleton — call get_engine() everywhere
# ---------------------------------------------------------------------------
_engine: Optional["FaceEngine"] = None


def get_engine() -> "FaceEngine":
    global _engine
    if _engine is None:
        _engine = FaceEngine()
    return _engine


# ---------------------------------------------------------------------------
class FaceEngine:
    """Thread-safe InsightFace wrapper (buffalo_l, CPU ONNX)."""

    def __init__(self) -> None:
        _log.info("[FaceEngine] Loading buffalo_l (first run downloads ~300 MB) …")
        self._app = FaceAnalysis(
            name="buffalo_l",
            providers=["CPUExecutionProvider"],
        )
        # 640×640 catches small faces on webcam while staying CPU-tractable
        self._app.prepare(ctx_id=0, det_size=(640, 640))
        _log.info("[FaceEngine] buffalo_l ready")

    # ------------------------------------------------------------------
    # Detection
    # ------------------------------------------------------------------
    def get_faces(self, img_bgr: np.ndarray) -> list:
        """All detected faces, sorted by det_score descending."""
        try:
            faces = self._app.get(img_bgr)
        except Exception as exc:
            _log.debug(f"[FaceEngine] detect error: {exc}")
            return []
        return sorted(faces, key=lambda f: float(f.det_score), reverse=True)

    def best_face(self, img_bgr: np.ndarray):
        """Highest-confidence face, or None."""
        faces = self.get_faces(img_bgr)
        return faces[0] if faces else None

    # ------------------------------------------------------------------
    # Embedding
    # ------------------------------------------------------------------
    def embed(self, img_bgr: np.ndarray) -> Optional[np.ndarray]:
        """Detect best face → return L2-normalised 512-dim ArcFace embedding."""
        face = self.best_face(img_bgr)
        if face is None:
            return None
        return l2_normalize(face.embedding)

    # ------------------------------------------------------------------
    # Distance
    # ------------------------------------------------------------------
    @staticmethod
    def cosine(a: np.ndarray, b: np.ndarray) -> float:
        na, nb = np.linalg.norm(a), np.linalg.norm(b)
        if na == 0.0 or nb == 0.0:
            return 1.0
        return float(1.0 - np.dot(a, b) / (na * nb))

    def best_match(self, live: np.ndarray, registered: list[np.ndarray]) -> float:
        """Min cosine distance between live embedding and every registered embedding.

        Multi-angle enrollment stores several registered embeddings (front, left,
        right, up, down).  Taking the minimum means the live frame only needs to
        match one registered angle — dramatically reduces false rejects.
        """
        if not registered:
            return 1.0
        return min(self.cosine(live, r) for r in registered)


# ---------------------------------------------------------------------------
# Shared normalisation helper (imported by main.py too)
# ---------------------------------------------------------------------------
def l2_normalize(v: np.ndarray) -> np.ndarray:
    n = np.linalg.norm(v)
    return v if n == 0.0 else v / n
