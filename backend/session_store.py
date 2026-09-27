import os
import sqlite3
import secrets
import time
from cryptography.fernet import Fernet

DB_PATH = os.getenv("TOKEN_DB", "tokens.sqlite3")

def cipher():
    key=os.getenv("TOKEN_ENCRYPTION_KEY")
    if not key:
        raise RuntimeError("TOKEN_ENCRYPTION_KEY is required for TikTok sessions")
    return Fernet(key.encode())

def encrypt(value:str)->str:
    return cipher().encrypt(value.encode()).decode()

def decrypt(value:str)->str:
    return cipher().decrypt(value.encode()).decode()

def connect():
    db = sqlite3.connect(DB_PATH)
    db.execute("""CREATE TABLE IF NOT EXISTS tiktok_sessions(
        session_id TEXT PRIMARY KEY,
        access_token TEXT NOT NULL,
        refresh_token TEXT NOT NULL,
        expires_at INTEGER DEFAULT 0
    )""")
    db.execute("""CREATE TABLE IF NOT EXISTS oauth_states(
        state TEXT PRIMARY KEY,
        created_at INTEGER NOT NULL
    )""")
    return db

def save(access_token: str, refresh_token: str, expires_at: int = 0) -> str:
    session_id = secrets.token_urlsafe(32)
    with connect() as db:
        db.execute("INSERT INTO tiktok_sessions VALUES(?,?,?,?)",
                   (session_id, encrypt(access_token), encrypt(refresh_token), expires_at))
    return session_id

def load(session_id: str):
    with connect() as db:
        row = db.execute(
            "SELECT access_token,refresh_token,expires_at FROM tiktok_sessions WHERE session_id=?",
            (session_id,)
        ).fetchone()
    if not row:
        return None
    return (decrypt(row[0]),decrypt(row[1]),row[2])

def update(session_id: str, access_token: str, refresh_token: str, expires_at: int = 0):
    with connect() as db:
        db.execute("""UPDATE tiktok_sessions
                      SET access_token=?,refresh_token=?,expires_at=?
                      WHERE session_id=?""",
                   (encrypt(access_token), encrypt(refresh_token), expires_at, session_id))


def create_oauth_state() -> str:
    state=secrets.token_urlsafe(32)
    now=int(time.time())
    with connect() as db:
        db.execute("DELETE FROM oauth_states WHERE created_at < ?",(now-900,))
        db.execute("INSERT INTO oauth_states VALUES(?,?)",(state,now))
    return state

def consume_oauth_state(state:str) -> bool:
    now=int(time.time())
    with connect() as db:
        row=db.execute("SELECT created_at FROM oauth_states WHERE state=?",(state,)).fetchone()
        db.execute("DELETE FROM oauth_states WHERE state=?",(state,))
    return bool(row and now-int(row[0]) <= 900)
