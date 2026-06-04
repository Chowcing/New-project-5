from __future__ import annotations

import os
import tempfile
import importlib
import sys
from pathlib import Path
from typing import Any, Protocol

from fastapi import FastAPI, File, HTTPException, UploadFile


class OcrEngine(Protocol):
    def recognize(self, image_bytes: bytes, filename: str) -> list[str]:
        ...


class PaddleOcrEngine:
    def __init__(self) -> None:
        self._ocr: Any | None = None

    def recognize(self, image_bytes: bytes, filename: str) -> list[str]:
        suffix = Path(filename).suffix or ".jpg"
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=True) as image_file:
            image_file.write(image_bytes)
            image_file.flush()
            result = self._pipeline().predict(image_file.name)
        return extract_texts(result)

    def _pipeline(self) -> Any:
        if self._ocr is None:
            patch_paddlex_headless_opencv_check()
            from paddleocr import PaddleOCR

            self._ocr = PaddleOCR(
                text_detection_model_name=os.getenv("PADDLEOCR_DET_MODEL", "PP-OCRv5_mobile_det"),
                text_recognition_model_name=os.getenv("PADDLEOCR_REC_MODEL", "PP-OCRv5_mobile_rec"),
                use_doc_orientation_classify=False,
                use_doc_unwarping=False,
                use_textline_orientation=False,
            )
        return self._ocr


def patch_paddlex_headless_opencv_check() -> None:
    try:
        deps = importlib.import_module("paddlex.utils.deps")
    except Exception:
        return

    if not hasattr(deps, "_expense_original_is_dep_available"):
        deps._expense_original_is_dep_available = deps.is_dep_available

        def is_dep_available(dep: str, /, *args: Any, **kwargs: Any) -> bool:
            if dep == "opencv-contrib-python":
                dep = "opencv-contrib-python-headless"
            return deps._expense_original_is_dep_available(dep, *args, **kwargs)

        deps.is_dep_available = is_dep_available

    for extra in ("ocr-core", "ocr"):
        requirements = deps.EXTRAS.get(extra)
        if not requirements or "opencv-contrib-python" not in requirements:
            continue
        requirements.pop("opencv-contrib-python", None)
        requirements["opencv-contrib-python-headless"] = ["opencv-contrib-python-headless==4.10.0.84"]
    deps.is_extra_available.cache_clear()
    try:
        cv2 = importlib.import_module("cv2")
    except Exception:
        return
    for module_name, module in list(sys.modules.items()):
        if module_name.startswith("paddlex.") and module is not None and not hasattr(module, "cv2"):
            setattr(module, "cv2", cv2)


def extract_texts(result: Any) -> list[str]:
    texts: list[str] = []
    for item in result or []:
        data = item
        if hasattr(item, "json"):
            data = item.json
        if callable(data):
            data = data()
        if hasattr(item, "to_dict"):
            data = item.to_dict()
        texts.extend(extract_texts_from_value(data))
    return [text for text in texts if text.strip()]


def extract_texts_from_value(value: Any) -> list[str]:
    if isinstance(value, dict):
        if isinstance(value.get("rec_texts"), list):
            return [str(text) for text in value["rec_texts"]]
        if isinstance(value.get("texts"), list):
            return [str(text) for text in value["texts"]]
        texts: list[str] = []
        for child in value.values():
            texts.extend(extract_texts_from_value(child))
        return texts
    if isinstance(value, (list, tuple)):
        texts: list[str] = []
        for child in value:
            texts.extend(extract_texts_from_value(child))
        return texts
    return []


def create_app(ocr_engine: OcrEngine | None = None) -> FastAPI:
    app = FastAPI(title="Expense Local OCR Service")
    engine = ocr_engine or PaddleOcrEngine()

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/ocr")
    async def recognize(image: UploadFile = File(...)) -> dict[str, str]:
        image_bytes = await image.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="请选择要识别的图片")
        texts = engine.recognize(image_bytes, image.filename or "image")
        return {"text": "\n".join(texts), "provider": "local-paddleocr"}

    return app


app = create_app()
