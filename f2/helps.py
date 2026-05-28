#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
@Description:helps.py
@Date       :2023/02/06 17:36:41
@Author     :JohnserfSeed
@version    :0.0.1.7
@License    :Apache License 2.0
@Github     :https://github.com/johnserf-seed
@Mail       :support@f2.wiki
-------------------------------------------------
Change Log  :
2023/02/06 17:36:41 - create output help
2024/03/11 18:23:30 - change get_help @ importlib path
2024/10/30 13:40:01 - make terminal more readable
-------------------------------------------------
"""

import f2
import importlib

from f2.cli.cli_console import RichConsoleManager
from f2.i18n.translator import _

console_manager = RichConsoleManager()
console = console_manager.rich_console


def get_help(app_name: str) -> None:
    try:
        module = importlib.import_module(f"f2.apps.{app_name}.help")
        if hasattr(module, "help"):
            module.help()
        else:
            console.print(
                _("[red]在 {0} 应用里没有找到帮助文件[/red]").format(app_name)
            )
    except ImportError:
        console.print(_("[red]没有找到 {0} 应用[/red]").format(app_name))


def main() -> None:
    console.print(console_manager.header("F2 DOWNLOAD ENGINE"))
    console.print(console_manager.line("VERSION", f2.__version__))
    console.print(console_manager.line("DESCRIPTION", "DouYin download tool"))
    console.print(console_manager.line("GITHUB", f2.__repourl__))
    console.print(console_manager.line("DOCS", f2.__docurl__))
    console.print(console_manager.header("USAGE"))
    console.print(console_manager.line("HELP", "命令帮助"))
    console.print(console_manager.line("COMMAND", "f2 <apps> [COMMAND]"))
    console.print(console_manager.line("EXAMPLE", "f2 dy -h/--help"))
    console.print(console_manager.line("DEBUG", "f2 -d DEBUG dy"))
    console.print(console_manager.header("APPS"))
    console.print(console_manager.line("douyin 或 dy", "READY"))
    console.print(console_manager.line("SCOPE", _("one/post/like/collection/live")))
    console.print(
        console_manager.line(
            "LOGS", _("调试日志位于 /logs；提交 Issue 前请删除个人敏感信息")
        )
    )
    console.print(console_manager.line("ISSUES", f"{f2.__repourl__}/issues"))
