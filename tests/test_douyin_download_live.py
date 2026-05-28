from f2.apps.douyin.dl import DouyinDownloader
from f2.cli.cli_console import RichConsoleManager


async def test_create_download_tasks_reuses_active_progress_live(tmp_path):
    kwargs = {
        "cookie": "",
        "headers": {},
        "proxies": {"http://": None, "https://": None},
        "interval": None,
        "folderize": False,
        "naming": "{create}_{desc}",
    }
    downloader = DouyinDownloader(kwargs)
    handled = []

    async def handler_download(kwargs, aweme_data, user_path):
        handled.append((kwargs, aweme_data, user_path))

    async def execute_tasks():
        return None

    downloader.handler_download = handler_download
    downloader.execute_tasks = execute_tasks

    with RichConsoleManager().progress:
        await downloader.create_download_tasks(
            kwargs,
            {"aweme_id": "7645046357233475173"},
            tmp_path,
        )

    assert len(handled) == 1
