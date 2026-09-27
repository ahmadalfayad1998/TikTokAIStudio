import os
import httpx

async def generate_vertical_image(prompt:str)->str:
    provider=os.getenv("IMAGE_PROVIDER","none").lower()
    if provider!="automatic1111":
        raise RuntimeError("IMAGE_PROVIDER is not configured")
    base=os.getenv("SD_WEBUI_URL","http://127.0.0.1:7860").rstrip("/")
    body={
        "prompt":prompt,
        "negative_prompt":"text, watermark, logo, low quality, blurry, deformed",
        "steps":20,
        "width":576,
        "height":1024,
        "cfg_scale":7,
    }
    async with httpx.AsyncClient(timeout=180) as client:
        r=await client.post(base+"/sdapi/v1/txt2img",json=body)
        r.raise_for_status()
        data=r.json()
    images=data.get("images") or []
    if not images:
        raise RuntimeError("Image provider returned no image")
    return images[0]
