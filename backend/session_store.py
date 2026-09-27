import os
import sqlite3
import secrets

DB_PATH = os.getenv("TOKEN_DB", "tokens.sqlite3")

def connect():
    db = sqlite3.connect(DB_PATH)
    db.execute("""CREATE TABLE IF NOT EXISTS tiktok_sessions(
        session_id TEXT PRIMARY KEY,
        access_token TEXT NOT NULL,
        refresh_token TEXT NOT NULL,
        expires_at INTEGER DEFAULT 0
    )""")
    return db

def save(access_token: str, refresh_token: str, expires_at: int = 0) -> str:
    session_id = secrets.token_urlsafe(32)
    with connect() as db:
        db.execute("INSERT INTO tiktok_sessions VALUES(?,?,?,?)",
                   (session_id, access_token, refresh_token, expires_at))
    return session_id

def load(session_id: str):
    with connect() as db:
        row = db.execute(
            "SELECT access_token,refresh_token,expires_at FROM tiktok_sessions WHERE session_id=?",
            (session_id,)
        ).fetchone()
    return row

def update(session_id: str, access_token: str, refresh_token: str, expires_at: int = 0):
    with connect() as db:
        db.execute("""UPDATE tiktok_sessions
                      SET access_token=?,refresh_token=?,expires_at=?
                      WHERE session_id=?""",
                   (access_token, refresh_token, expires_at, session_id))
