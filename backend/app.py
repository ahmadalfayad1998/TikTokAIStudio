import json, os, re
from typing import Literal
import httpx
from fastapi import Header
from tiktok_service import creator_info, post_status, exchange_code, refresh_token
import session_store
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app=FastAPI(title="TikTokAIStudio Backend",version="1.0.0")

class GenerateRequest(BaseModel):
    topic:str=Field(min_length=2,max_length=300)
    language:str="ar"

SYSTEM="""Return ONLY valid JSON with keys title, hook, description, hashtags, scenes.
hashtags must be an array of strings. scenes must be an array of 5 concise scene texts.
Create original short-form social video content. Do not include markdown fences."""

def parse_json(text:str):
    text=re.sub(r"^\s*```(?:json)?|\```\s*$","",text.strip(),flags=re.I)
    data=json.loads(text)
    return {"title":str(data.get("title","")),"hook":str(data.get("hook","")),
            "description":str(data.get("description","")),
            "hashtags":[str(x) for x in data.get("hashtags",[])][:12],
            "scenes":[str(x) for x in data.get("scenes",[])][:8]}

async def ollama(prompt:str):
    url=os.getenv("OLLAMA_URL","http://127.0.0.1:11434").rstrip("/")+"/api/chat"
    model=os.getenv("OLLAMA_MODEL","llama3.2")
    async with httpx.AsyncClient(timeout=90) as client:
        r=await client.post(url,json={"model":model,"stream":False,"format":"json","messages":[
            {"role":"system","content":SYSTEM},{"role":"user","content":prompt}]})
        r.raise_for_status()
        return r.json()["message"]["content"]

async def compatible(prompt:str):
    base=os.environ["OPENAI_BASE_URL"].rstrip("/")
    key=os.environ["OPENAI_API_KEY"]
    model=os.environ["OPENAI_MODEL"]
    async with httpx.AsyncClient(timeout=90) as client:
        r=await client.post(base+"/chat/completions",headers={"Authorization":"Bearer "+key},
          json={"model":model,"temperature":0.7,"messages":[{"role":"system","content":SYSTEM},{"role":"user","content":prompt}]})
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"]

@app.get("/health")
def health(): return {"ok":True,"provider":os.getenv("AI_PROVIDER","ollama")}

@app.post("/generate")
async def generate(req:GenerateRequest):
    prompt=f"Language: {req.language}\nTopic: {req.topic}\nCreate a compelling vertical short-video package."
    try:
        provider=os.getenv("AI_PROVIDER","ollama").lower()
        raw=await (compatible(prompt) if provider=="openai_compatible" else ollama(prompt))
        data=parse_json(raw)
        if not data["scenes"]: raise ValueError("No scenes returned")
        return {"data":data}
    except Exception as e:
        raise HTTPException(status_code=502,detail=f"AI generation failed: {type(e).__name__}")


class TikTokExchangeRequest(BaseModel):
    code: str
    redirect_uri: str
    code_verifier: str

@app.post("/oauth/tiktok/exchange")
async def tiktok_exchange(req:TikTokExchangeRequest):
    try:
        bundle=await exchange_code(req.code,req.redirect_uri,req.code_verifier)
        sid=session_store.save(bundle["access_token"],bundle["refresh_token"],int(bundle.get("expires_in",0)))
        return {"session_id":sid,"scope":bundle.get("scope",""),"open_id":bundle.get("open_id","")}
    except Exception as e:
        raise HTTPException(status_code=502,detail=f"TikTok authorization failed: {type(e).__name__}")

@app.get("/tiktok/creator-info")
async def tiktok_creator(session: str = Header(alias="X-App-Session")):
    row=session_store.load(session)
    if not row: raise HTTPException(status_code=401,detail="Invalid TikTok session")
    try: return await creator_info(row[0])
    except Exception as e: raise HTTPException(status_code=502,detail=f"TikTok creator query failed: {type(e).__name__}")
