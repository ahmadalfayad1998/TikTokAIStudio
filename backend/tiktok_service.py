import os, math
import httpx

API="https://open.tiktokapis.com"

def _credentials():
    key=os.getenv("TIKTOK_CLIENT_KEY")
    secret=os.getenv("TIKTOK_CLIENT_SECRET")
    if not key or not secret: raise RuntimeError("TikTok server credentials are not configured")
    return key,secret

async def exchange_code(code:str,redirect_uri:str,code_verifier:str):
    key,secret=_credentials()
    data={"client_key":key,"client_secret":secret,"code":code,
          "grant_type":"authorization_code","redirect_uri":redirect_uri,
          "code_verifier":code_verifier}
    async with httpx.AsyncClient(timeout=30) as c:
        r=await c.post(API+"/v2/oauth/token/",data=data)
        r.raise_for_status(); return r.json()

async def refresh_token(refresh:str):
    key,secret=_credentials()
    async with httpx.AsyncClient(timeout=30) as c:
        r=await c.post(API+"/v2/oauth/token/",data={"client_key":key,"client_secret":secret,
            "grant_type":"refresh_token","refresh_token":refresh})
        r.raise_for_status(); return r.json()

async def creator_info(access:str):
    async with httpx.AsyncClient(timeout=30) as c:
        r=await c.post(API+"/v2/post/publish/creator_info/query/",
            headers={"Authorization":"Bearer "+access,"Content-Type":"application/json; charset=UTF-8"},
            content=b"{}")
        r.raise_for_status(); return r.json()

def chunk_plan(size:int):
    if size<=0: raise ValueError("empty video")
    if size<5_000_000: return size,1
    if size<=64_000_000: return size,1
    chunk=64_000_000
    count=size//chunk
    if count>1000: raise ValueError("video requires too many chunks")
    return chunk,int(count)

async def init_direct_post(access:str,size:int,title:str,privacy:str,
                           disable_comment=False,disable_duet=False,disable_stitch=False):
    chunk,count=chunk_plan(size)
    body={"post_info":{"title":title[:2200],"privacy_level":privacy,
        "disable_comment":disable_comment,"disable_duet":disable_duet,
        "disable_stitch":disable_stitch,"is_aigc":True},
        "source_info":{"source":"FILE_UPLOAD","video_size":size,
        "chunk_size":chunk,"total_chunk_count":count}}
    async with httpx.AsyncClient(timeout=30) as c:
        r=await c.post(API+"/v2/post/publish/video/init/",
            headers={"Authorization":"Bearer "+access,"Content-Type":"application/json; charset=UTF-8"},json=body)
        r.raise_for_status(); return r.json()

async def post_status(access:str,publish_id:str):
    async with httpx.AsyncClient(timeout=30) as c:
        r=await c.post(API+"/v2/post/publish/status/fetch/",
            headers={"Authorization":"Bearer "+access,"Content-Type":"application/json; charset=UTF-8"},
            json={"publish_id":publish_id})
        r.raise_for_status(); return r.json()
