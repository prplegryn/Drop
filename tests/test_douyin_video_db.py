import sqlite3

from f2.apps.douyin.db import AsyncVideoDB


async def test_video_db_migrates_caption_columns_and_filters_unknown_fields(tmp_path):
    db_path = tmp_path / "douyin_videos.db"

    conn = sqlite3.connect(db_path)
    conn.execute("CREATE TABLE video_info (aweme_id TEXT PRIMARY KEY, desc TEXT)")
    conn.commit()
    conn.close()

    async with AsyncVideoDB(str(db_path)) as db:
        columns = await db._get_table_columns()

        assert "caption" in columns
        assert "caption_raw" in columns

        await db.add_video_info(
            aweme_id="7645020099694744186",
            caption="test caption",
            caption_raw="test caption raw",
            desc="test desc",
            missing_column="ignored",
        )

        row = await db.fetch_one(
            "SELECT aweme_id, caption, caption_raw, desc FROM video_info WHERE aweme_id=?",
            ("7645020099694744186",),
        )

    assert row == (
        "7645020099694744186",
        "test caption",
        "test caption raw",
        "test desc",
    )
