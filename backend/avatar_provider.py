import asyncio
import os
from typing import Any

import httpx

DID_BASE="https://api.d-id.com"

def _settings() -> tuple[str,str,str,float,int]:
    key=os.getenv("DID_API_KEY","").strip()
    presenter=os.getenv("DID_PRESENTER_URL","").strip()
    voice=os.getenv("DID_VOICE_ID","ar-SA-HamedNeural").strip() or "ar-SA-HamedNeural"
    rate=float(os.getenv("DID_VOICE_RATE","0.95"))
    timeout=int(os.getenv("DID_TIMEOUT_SECONDS","120"))
    if not key:
        raise RuntimeError("DID_API_KEY is not configured")
    if not presenter:
        raise RuntimeError("DID_PRESENTER_URL is not configured")
    if not presenter.startswith("https://"):
        raise RuntimeError("DID_PRESENTER_URL must use HTTPS")
    return key,presenter,voice,rate,timeout

def _talk_payload(text:str,presenter:str,voice:str,rate:float) -> dict[str,Any]:
    text=" ".join(text.split()).strip()
    if not text:
        raise RuntimeError("Presenter text is empty")
    if len(text)>3500:
        text=text[:3500]
    return {
        "source_url":presenter,
        "script":{
            "type":"text",
            "input":text,
            "provider":{
                "type":"microsoft",
                "voice_id":voice,
                "voice_config":{"rate":str(rate)},
            },
        },
        "config":{"fluent":True},
        "name":"TikTokAIStudio presenter",
    }

async def generate_presenter_video(text:str) -> dict[str,str]:
    key,presenter,voice,rate,timeout=_settings()
    headers={
        "Authorization":"Basic "+key,
        "Content-Type":"application/json",
        "Accept":"application/json",
    }
    payload=_talk_payload(text,presenter,voice,rate)

    async with httpx.AsyncClient(timeout=45,follow_redirects=True) as client:
        created=await client.post(DID_BASE+"/talks",headers=headers,json=payload)
        created.raise_for_status()
        created_data=created.json()
        talk_id=str(created_data.get("id") or "")
        if not talk_id:
            raise RuntimeError("D-ID response is missing talk id")

        deadline=asyncio.get_running_loop().time()+max(30,timeout)
        last_status="created"
        while asyncio.get_running_loop().time()<deadline:
            await asyncio.sleep(2)
            result=await client.get(DID_BASE+"/talks/"+talk_id,headers=headers)
            result.raise_for_status()
            data=result.json()
            last_status=str(data.get("status") or "")
            if last_status=="done":
                url=str(data.get("result_url") or "")
                if not url.startswith("https://"):
                    raise RuntimeError("D-ID result URL is missing")
                return {
                    "video_url":url,
                    "talk_id":talk_id,
                    "voice_id":voice,
                    "provider":"d-id",
                }
            if last_status in {"error","failed","rejected"}:
                message=str(data.get("error") or data.get("error_description") or last_status)
                raise RuntimeError("D-ID generation failed: "+message[:300])

    raise RuntimeError("D-ID generation timed out with status: "+last_status)
