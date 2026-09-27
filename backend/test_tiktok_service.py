from tiktok_service import chunk_plan

def test_small_upload_is_single_chunk():
    assert chunk_plan(4_000_000)==(4_000_000,1)

def test_medium_upload_is_single_chunk():
    assert chunk_plan(50_000_000)==(50_000_000,1)

def test_large_upload_uses_10mb_chunks_and_valid_last_chunk():
    chunk,count=chunk_plan(95_000_000)
    assert chunk==10_000_000
    assert count==9
    assert 5_000_000 <= 95_000_000-(count*chunk) <= 64_000_000

def test_empty_rejected():
    try: chunk_plan(0)
    except ValueError: return
    raise AssertionError("empty video must be rejected")
