import base64
import io

from PIL import Image
import pytesseract


def image_base64_to_text(image_base64: str) -> str:
    try:
        image_bytes = base64.b64decode(image_base64)
        image = Image.open(io.BytesIO(image_bytes))
        image = image.convert("L")
        text = pytesseract.image_to_string(image, lang="eng")
        return text.strip()
    except Exception as exc:
        raise RuntimeError(f"OCR failed: {exc}") from exc
