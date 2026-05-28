import asyncio
import os
import re
import sys
import traceback
import types
from datetime import datetime


DOUYIN_URL_RE = re.compile(r"https?://(?:[\w.-]+\.)?douyin\.com/[^\s，。；;]+/?")
ANSI_RE = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")
TRAILING_PUNCTUATION = "，。；;、!！?？)）]】>》\"'"


def parse_douyin_url(text):
    match = DOUYIN_URL_RE.search(text or "")
    if not match:
        return ""
    return match.group(0).rstrip(TRAILING_PUNCTUATION)


def _line(label, value=""):
    timestamp = datetime.now().strftime("%H:%M:%S")
    if value == "":
        return f"[{timestamp}] {label}"
    return f"[{timestamp}] {label:<13}: {value}"


def _emit(callback, line):
    callback.onLine(str(line))


def _install_android_stubs():
    if "browser_cookie3" in sys.modules:
        return

    module = types.ModuleType("browser_cookie3")

    def unavailable(*args, **kwargs):
        raise RuntimeError("browser cookie import is not available in the Android app")

    for name in (
        "chrome",
        "firefox",
        "edge",
        "opera",
        "opera_gx",
        "safari",
        "chromium",
        "brave",
        "vivaldi",
        "librewolf",
    ):
        setattr(module, name, unavailable)

    sys.modules["browser_cookie3"] = module


class OutputBridge:
    def __init__(self, callback, secret=""):
        self.callback = callback
        self.secret = secret or ""
        self.buffer = ""
        self.encoding = "utf-8"

    def write(self, data):
        if not data:
            return 0

        text = str(data).replace("\r", "\n")
        self.buffer += text

        while "\n" in self.buffer:
            line, self.buffer = self.buffer.split("\n", 1)
            self._emit_line(line)

        return len(data)

    def flush(self):
        if self.buffer:
            self._emit_line(self.buffer)
            self.buffer = ""

    def isatty(self):
        return False

    def fileno(self):
        raise OSError("Android output bridge has no file descriptor")

    def _emit_line(self, line):
        line = ANSI_RE.sub("", line).rstrip()
        if not line.strip():
            return
        if self.secret:
            line = line.replace(self.secret, "<COOKIE>")
        _emit(self.callback, line)


class RedirectOutput:
    def __init__(self, callback, secret=""):
        self.bridge = OutputBridge(callback, secret)
        self.old_stdout = None
        self.old_stderr = None

    def __enter__(self):
        self.old_stdout = sys.stdout
        self.old_stderr = sys.stderr
        sys.stdout = self.bridge
        sys.stderr = self.bridge
        return self.bridge

    def __exit__(self, exc_type, exc, tb):
        self.bridge.flush()
        sys.stdout = self.old_stdout
        sys.stderr = self.old_stderr


def _build_kwargs(mode, url, cookie, download_dir):
    from f2.apps.douyin.utils import ClientConfManager

    return {
        "app_name": "douyin",
        "mode": mode,
        "url": url,
        "cookie": cookie,
        "headers": {
            "User-Agent": ClientConfManager.user_agent(),
            "Referer": ClientConfManager.referer(),
        },
        "proxies": ClientConfManager.proxies(),
        "path": download_dir,
        "folderize": False,
        "naming": "{create}_{desc}",
        "timeout": 10,
        "max_retries": 5,
        "max_connections": 5,
        "max_tasks": 10,
        "max_counts": 0,
        "page_counts": 20,
        "interval": None,
        "music": False,
        "cover": False,
        "desc": False,
        "lyric": True,
    }


def _reset_f2_output(download_dir):
    import logging

    from f2.cli.cli_console import RichConsoleManager
    from f2.log.logger import LogManager

    RichConsoleManager.reset_instance()

    for name, to_console in (("f2", True), ("f2-trace", False)):
        manager = LogManager(name)
        manager.shutdown()
        manager.setup_logging(
            level=logging.INFO,
            log_to_console=to_console,
            log_path=None,
        )
        if name == "f2":
            for handler in logging.getLogger(name).handlers:
                if hasattr(handler, "_engine_printed"):
                    handler._engine_printed = True


def run(mode, raw_text, cookie, download_dir, callback):
    mode = "post" if mode == "post" else "one"
    url = parse_douyin_url(raw_text)
    cookie = cookie or ""
    download_dir = download_dir or os.environ.get("HOME", ".")

    if not url:
        _emit(callback, _line("STATUS", "未找到抖音链接"))
        return False

    if not cookie.strip():
        _emit(callback, _line("STATUS", "未设置 Cookie"))
        return False

    os.makedirs(download_dir, exist_ok=True)
    os.chdir(download_dir)
    _install_android_stubs()

    with RedirectOutput(callback, cookie):
        try:
            _reset_f2_output(download_dir)
            from f2.apps.douyin.handler import main

            kwargs = _build_kwargs(mode, url, cookie, download_dir)
            asyncio.run(main(kwargs))
            return True
        except BaseException as exc:
            _emit(callback, _line("ERROR", str(exc).replace(cookie, "<COOKIE>")))
            traceback.print_exc()
            return False
