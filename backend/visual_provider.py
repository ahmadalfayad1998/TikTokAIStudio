import base64
import hashlib
import json
import os
import re
from pathlib import Path
from typing import Any

import httpx

CACHE_DIR = Path(os.getenv("VISUAL_CACHE_DIR", "state/visual_cache"))
CACHE_DIR.mkdir(parents=True, exist_ok=True)

def _provider() -> str:
    return os.getenv("IMAGE_PROVIDER", "none").strip().lower()

def _cache_key(provider: str, prompt: str) -> str:
    raw = (provider + "\n" + prompt.strip()).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()

def _cache_path(provider: str, prompt: str) -> Path:
    return CACHE_DIR / (_cache_key(provider, prompt) + ".json")

def _read_cache(provider: str, prompt: str) -> dict[str, Any] | None:
    path = _cache_path(provider, prompt)
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text("utf-8"))
        if isinstance(data, dict) and data.get("image"):
            return data
    except Exception:
        return None
    return None

def _write_cache(provider: str, prompt: str, payload: dict[str, Any]) -> None:
    path = _cache_path(provider, prompt)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload, ensure_ascii=False), "utf-8")
    tmp.replace(path)

def _pexels_query(prompt: str) -> str:
    text = re.sub(r"\b(vertical|9:16|cinematic|editorial|scene|no text|no watermark|realistic|premium|dramatic|lighting)\b", " ", prompt, flags=re.I)
    text = re.sub(r"[^\w\s-]", " ", text, flags=re.UNICODE)
    words = [w for w in re.split(r"\s+", text.strip()) if len(w) >= 3]
    if not words:
        return "technology people"
    return " ".join(words[:10])[:120]

async def _automatic1111(prompt: str) -> dict[str, Any]:
    base = os.getenv("SD_WEBUI_URL", "http://127.0.0.1:7860").rstrip("/")
    body = {
        "prompt": prompt,
        "negative_prompt": "text, watermark, logo, low quality, blurry, deformed, duplicate",
        "steps": int(os.getenv("SD_STEPS", "24")),
        "width": 576,
        "height": 1024,
        "cfg_scale": float(os.getenv("SD_CFG_SCALE", "7")),
    }
    async with httpx.AsyncClient(timeout=180) as client:
        response = await client.post(base + "/sdapi/v1/txt2img", json=body)
        response.raise_for_status()
        data = response.json()
    images = data.get("images") or []
    if not images:
        raise RuntimeError("Image provider returned no image")
    return {
        "image": images[0],
        "provider": "automatic1111",
        "credit": "",
        "source_url": "",
    }

async def _pexels(prompt: str) -> dict[str, Any]:
    key = os.getenv("PEXELS_API_KEY", "").strip()
    if not key:
        raise RuntimeError("PEXELS_API_KEY is not configured")

    query = _pexels_query(prompt)
    params = {
        "query": query,
        "orientation": "portrait",
        "size": "medium",
        "per_page": 12,
    }
    async with httpx.AsyncClient(timeout=45, follow_redirects=True) as client:
        response = await client.get(
            "https://api.pexels.com/v1/search",
            params=params,
            headers={"Authorization": key},
        )
        response.raise_for_status()
        photos = response.json().get("photos") or []
        if not photos:
            raise RuntimeError("Pexels returned no matching photo")

        # Deterministic selection spreads consecutive scene prompts across the result set.
        pick = int(hashlib.sha256(prompt.encode("utf-8")).hexdigest()[:8], 16) % len(photos)
        photo = photos[pick]
        src = photo.get("src") or {}
        image_url = src.get("portrait") or src.get("large2x") or src.get("large") or src.get("original")
        if not image_url:
            raise RuntimeError("Pexels photo has no downloadable source")

        image_response = await client.get(image_url)
        image_response.raise_for_status()
        if len(image_response.content) > 12 * 1024 * 1024:
            raise RuntimeError("Downloaded photo is too large")

    encoded = base64.b64encode(image_response.content).decode("ascii")
    photographer = str(photo.get("photographer") or "Pexels contributor")
    photographer_url = str(photo.get("photographer_url") or "")
    source_url = str(photo.get("url") or photographer_url)
    return {
        "image": encoded,
        "provider": "pexels",
        "credit": photographer,
        "source_url": source_url,
    }

async def generate_vertical_image(prompt: str) -> dict[str, Any]:
    prompt = prompt.strip()
    if not prompt:
        raise RuntimeError("Image prompt is empty")

    provider = _provider()
    if provider == "none":
        raise RuntimeError("IMAGE_PROVIDER is not configured")

    cached = _read_cache(provider, prompt)
    if cached:
        cached["cached"] = True
        return cached

    if provider == "automatic1111":
        payload = await _automatic1111(prompt)
    elif provider == "pexels":
        payload = await _pexels(prompt)
    else:
        raise RuntimeError("Unsupported IMAGE_PROVIDER: " + provider)

    payload["cached"] = False
    _write_cache(provider, prompt, payload)
    return payload
