import sqlite3
from cryptography.fernet import Fernet
import session_store

def configure(tmp_path,monkeypatch):
    db=tmp_path/"tokens.sqlite3"
    monkeypatch.setattr(session_store,"DB_PATH",str(db))
    monkeypatch.setenv("TOKEN_ENCRYPTION_KEY",Fernet.generate_key().decode())
    return db

def test_tokens_are_encrypted_at_rest_and_decrypt_on_load(tmp_path,monkeypatch):
    db=configure(tmp_path,monkeypatch)
    sid=session_store.save("access-secret","refresh-secret",123456)
    loaded=session_store.load(sid)
    assert loaded==("access-secret","refresh-secret",123456)

    con=sqlite3.connect(db)
    raw=con.execute(
        "SELECT access_token,refresh_token FROM tiktok_sessions WHERE session_id=?",
        (sid,)
    ).fetchone()
    con.close()
    assert raw is not None
    assert raw[0]!="access-secret"
    assert raw[1]!="refresh-secret"
    assert "access-secret" not in raw[0]
    assert "refresh-secret" not in raw[1]

def test_oauth_state_is_one_time(tmp_path,monkeypatch):
    configure(tmp_path,monkeypatch)
    state=session_store.create_oauth_state()
    assert session_store.consume_oauth_state(state) is True
    assert session_store.consume_oauth_state(state) is False
