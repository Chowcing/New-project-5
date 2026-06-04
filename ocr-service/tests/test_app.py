from app import create_app
from fastapi.testclient import TestClient


class FakeOcrEngine:
    def recognize(self, image_bytes: bytes, filename: str) -> list[str]:
        assert image_bytes.startswith(b"\xff\xd8\xff")
        assert filename == "receipt.jpg"
        return ["午餐", "25 元"]


def test_ocr_endpoint_returns_joined_text_from_engine():
    client = TestClient(create_app(FakeOcrEngine()))

    response = client.post(
        "/ocr",
        files={"image": ("receipt.jpg", b"\xff\xd8\xff\x01", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json() == {"text": "午餐\n25 元", "provider": "local-paddleocr"}


def test_ocr_endpoint_rejects_empty_file():
    client = TestClient(create_app(FakeOcrEngine()))

    response = client.post(
        "/ocr",
        files={"image": ("empty.jpg", b"", "image/jpeg")},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "请选择要识别的图片"


def test_extract_texts_ignores_metadata_strings():
    from app import extract_texts

    result = [{"input_path": "/tmp/receipt.jpg", "res": {"rec_texts": ["超市", "18 元"]}}]

    assert extract_texts(result) == ["超市", "18 元"]


def test_patch_paddlex_headless_opencv_check(monkeypatch):
    import collections
    import types
    import sys
    from app import patch_paddlex_headless_opencv_check

    def original_is_dep_available(dep: str, *args, **kwargs) -> bool:
        return dep == "opencv-contrib-python-headless"

    fake_deps = types.SimpleNamespace(
        EXTRAS={
            "ocr-core": collections.defaultdict(list, {"opencv-contrib-python": ["opencv-contrib-python==4.10.0.84"]}),
            "ocr": collections.defaultdict(list, {"opencv-contrib-python": ["opencv-contrib-python==4.10.0.84"]}),
        },
        is_dep_available=original_is_dep_available,
        is_extra_available=types.SimpleNamespace(cache_clear=lambda: None),
    )
    monkeypatch.setitem(sys.modules, "paddlex", types.SimpleNamespace())
    monkeypatch.setitem(sys.modules, "paddlex.utils", types.SimpleNamespace())
    monkeypatch.setitem(sys.modules, "paddlex.utils.deps", fake_deps)
    fake_cv2 = types.SimpleNamespace(IMREAD_COLOR=1)
    fake_reader = types.SimpleNamespace()
    monkeypatch.setitem(sys.modules, "cv2", fake_cv2)
    monkeypatch.setitem(sys.modules, "paddlex.inference.common.reader.image_reader", fake_reader)

    patch_paddlex_headless_opencv_check()

    assert "opencv-contrib-python" not in fake_deps.EXTRAS["ocr-core"]
    assert fake_deps.EXTRAS["ocr-core"]["opencv-contrib-python-headless"] == ["opencv-contrib-python-headless==4.10.0.84"]
    assert fake_deps.is_dep_available("opencv-contrib-python") is True
    assert fake_reader.cv2 is fake_cv2
