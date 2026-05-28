# path: f2/cli/cli_console.py

import datetime

from asyncio import Lock
from typing import Optional, Dict
from rich.prompt import Prompt
from rich.text import Text
from rich.theme import Theme
from rich.console import Console
from rich.spinner import Spinner
from rich.progress import (
    Progress as RichProgress,
    TaskID,
    Task,
    ProgressColumn,
)

from f2.utils._singleton import Singleton


CLI_THEME = Theme(
    {
        "f2.accent": "bright_cyan",
        "f2.dim": "grey50",
        "f2.file": "white",
        "f2.ok": "green",
        "f2.warn": "yellow",
        "f2.error": "red",
        "f2.rule": "grey35",
        "f2.status.wait": "grey50",
        "f2.status.run": "bright_cyan",
        "f2.status.ok": "green",
        "f2.status.warn": "yellow",
        "f2.status.error": "red",
        "bar.back": "grey23",
        "bar.complete": "bright_cyan",
        "bar.finished": "green",
        "bar.pulse": "cyan",
        "progress.percentage": "bright_cyan",
        "progress.spinner": "bright_cyan",
        "logging.level.debug": "grey50",
        "logging.level.info": "bright_cyan",
        "logging.level.warning": "yellow",
        "logging.level.error": "red",
        "logging.level.critical": "bold red",
    }
)

LABEL_WIDTH = 14
TRANSFER_BAR_WIDTH = 30
CURSOR = "█"


STATUS_LABELS = {
    "waiting": "WAIT",
    "starting": "INIT",
    "downloading": "GET",
    "paused": "HOLD",
    "warning": "WARN",
    "error": "FAIL",
    "missing": "MISS",
    "skipped": "SKIP",
    "completed": "DONE",
}


def status_label(state: str) -> str:
    return STATUS_LABELS.get(state, STATUS_LABELS["starting"])


def timestamp() -> str:
    return datetime.datetime.now().strftime("[%H:%M:%S]")


def dashboard_header(title: str, cursor: bool = False) -> Text:
    line = Text(f"{timestamp()} {title}")
    if cursor:
        line.append(f" {CURSOR}", style="bold blink")
    return line


def dashboard_line(label: str, value: str = "", cursor: bool = False) -> Text:
    line = Text(f"{timestamp()} {label:<{LABEL_WIDTH}}: {value}")
    if cursor:
        line.append(f" {CURSOR}", style="bold blink")
    return line


def format_duration(seconds: Optional[float]) -> str:
    if seconds is None or seconds == float("inf"):
        return "--:--"

    seconds = max(0, int(seconds))
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"

    return f"{minutes:02d}:{seconds:02d}"


def format_speed(bytes_per_second: Optional[float]) -> str:
    if not bytes_per_second:
        return "-- MB/s"

    units = ("B/s", "KB/s", "MB/s", "GB/s")
    speed = float(bytes_per_second)
    unit = units[0]
    for unit in units:
        if speed < 1024 or unit == units[-1]:
            break
        speed /= 1024

    if unit == "B/s":
        return f"{speed:.0f} {unit}"

    return f"{speed:.1f} {unit}"


class DashboardTimeColumn(ProgressColumn):
    def render(self, task: Task):
        return Text(timestamp(), style="f2.dim")


class DashboardTransferColumn(ProgressColumn):
    def render(self, task: Task):
        percentage = task.percentage if task.percentage is not None else 0
        percentage = max(0, min(100, percentage))
        filled = int(TRANSFER_BAR_WIDTH * percentage / 100)
        bar = CURSOR * filled + " " * (TRANSFER_BAR_WIDTH - filled)
        return Text(f"Transfer      : {bar} {percentage:>3.0f}%")


class DashboardSpeedColumn(ProgressColumn):
    def render(self, task: Task):
        return Text(f"Speed         : {format_speed(task.speed)}")


class DashboardETAColumn(ProgressColumn):
    def render(self, task: Task):
        return Text(f"ETA           : {format_duration(task.time_remaining)}")


class DashboardCursorColumn(ProgressColumn):
    def render(self, task: Task):
        return Text(CURSOR, style="bold blink")


class TaskStatusColumn(ProgressColumn):
    """Render a compact task state label."""

    def render(self, task: Task):
        state = task.fields.get("state", "starting")
        return Text(status_label(state).rjust(4), style="f2.dim")


class CustomSpinnerColumn(ProgressColumn):
    """
    CustomSpinnerColumn 类用于创建和管理自定义的进度指示器列。通过此类，可以为不同的状态（如等待、开始、下载、暂停等）配置不同的旋转动画。

    该类继承自 ProgressColumn，利用 rich 库中的 Spinner 来实现多种旋转动画效果。

    类属性:
    - DEFAULT_SPINNERS: 默认的各个状态对应的旋转动画类型。
    - spinners: 用于存储不同状态的 Spinner 对象。

    类方法:
    - __init__: 初始化 CustomSpinnerColumn 实例，根据传入的 spinner_styles 配置不同状态的旋转动画。
    - render: 渲染当前任务的进度指示器，根据任务状态（state）选择合适的旋转动画并返回其渲染结果。

    使用示例:
    ```python
        # 自定义不同状态的旋转动画
        custom_spinner_column = CustomSpinnerColumn(
            spinner_styles={
                "waiting": "dots8",
                "starting": "arrow",
                "downloading": "moon",
                "paused": "smiley",
                "error": "star2",
                "completed": "hearts",
            }
        )

        # 渲染自定义的进度指示器
        custom_spinner_column.render(task)
    ```
    """

    DEFAULT_SPINNERS = {
        "waiting": "simpleDots",
        "starting": "point",
        "downloading": "line",
        "paused": "dots8",
        "warning": "bouncingBar",
        "error": "simpleDotsScrolling",
        "missing": "simpleDotsScrolling",
        "skipped": "simpleDots",
        "completed": "point",
    }

    def __init__(
        self,
        spinner_styles: Optional[Dict[str, str]] = None,
        style: str = "progress.spinner",
        speed: float = 1.0,
    ):
        spinner_styles = spinner_styles or {}
        spinner_names = {
            state: spinner_styles.get(state, default)
            for state, default in self.DEFAULT_SPINNERS.items()
        }
        self.spinners = {
            state: Spinner(spinner_name, style=style, speed=speed)
            for state, spinner_name in spinner_names.items()
        }
        super().__init__()

    def render(self, task: Task):
        t = task.get_time()
        state = task.fields.get("state", "starting")
        spinner = self.spinners.get(state, self.spinners["starting"])
        return spinner.render(t)


class ProgressManager:
    """
    ProgressManager 类用于管理进度条和任务的显示，允许在控制台中显示多个并行任务的进度信息。

    该类封装了 rich 库的进度条功能，支持自定义进度列、自定义spinner列、任务更新、任务启动和停止等操作。

    类属性:
    - DEFAULT_COLUMNS: 默认的进度条列，包括spinner列、描述列、进度条列等。
    - _progress: rich 库中的 Progress 实例，负责绘制进度条。
    - _progress_lock: 用于同步进度条更新的锁。
    - _active_tasks: 存储正在进行中的任务 ID。

    方法:
    - __init__: 初始化 ProgressManager 实例，允许传入自定义列、进度条宽度等参数。
    - start: 启动进度条显示。
    - start_task: 启动一个具体任务的进度显示。
    - stop: 停止进度条显示。
    - stop_task: 停止一个具体任务的进度显示。
    - add_task: 异步添加一个新任务到进度条管理器中。
    - update: 异步更新任务的进度、描述、状态等信息。
    - __enter__: 启动进度条并支持使用 `with` 语法块。
    - __exit__: 停止进度条，确保任务完成。

    使用示例:
    ```python
        # 创建并启动进度管理器
        progress_manager = ProgressManager()
        progress_manager.start()

        # 添加任务并更新进度
        task_id = await progress_manager.add_task("任务描述", total=100)
        await progress_manager.update(task_id, completed=50)

        # 停止任务
        await progress_manager.stop_task(task_id)
    ```
    """

    @staticmethod
    def _default_columns() -> Dict[str, ProgressColumn]:
        return {
            "time": DashboardTimeColumn(),
            "transfer": DashboardTransferColumn(),
            "speed": DashboardSpeedColumn(),
            "eta": DashboardETAColumn(),
            "cursor": DashboardCursorColumn(),
        }

    def __init__(
        self,
        spinner_column: CustomSpinnerColumn = None,
        custom_columns: Optional[Dict[str, ProgressColumn]] = None,
        bar_width: Optional[int] = None,
        expand: bool = False,
        console: Optional[Console] = None,
    ):
        chosen_columns_dict = custom_columns or self._default_columns()
        if spinner_column and custom_columns:
            chosen_columns_dict["spinner"] = spinner_column
        self._console = console
        self._progress = RichProgress(
            *chosen_columns_dict.values(),
            console=console,
            refresh_per_second=8,
            transient=False,
            expand=expand,
        )
        self._progress_lock = Lock()
        self._active_tasks = set()
        self._start_count = 0
        self._visible_task_id = None
        self._had_tasks = False

    def start(self):
        if self._start_count == 0:
            self._progress.start()
        self._start_count += 1

    def start_task(self, task_id):
        self._progress.start_task(task_id)

    def stop(self):
        if self._start_count == 0:
            return

        self._start_count -= 1
        if self._start_count == 0:
            self._progress.stop()
            if self._console and self._had_tasks:
                self._console.print(dashboard_line("STATUS", "下载完成", cursor=True))

    def stop_task(self, task_id):
        self._progress.stop_task(task_id)

    @property
    def tasks(self):
        return self._progress.tasks

    async def add_task(
        self,
        description: str,
        start: bool = True,
        total: Optional[float] = None,
        completed: int = 0,
        visible: bool = True,
        state: str = "starting",
        filename: str = "",
    ) -> TaskID:
        async with self._progress_lock:
            self._had_tasks = True
            if visible:
                for task in self._progress.tasks:
                    self._progress.update(task.id, visible=False)

            task_id = self._progress.add_task(
                description=description,
                start=start,
                total=total,
                completed=completed,
                visible=visible,
                filename=filename,
                state=state,
            )
            if visible:
                self._visible_task_id = task_id
            self._active_tasks.add(task_id)
        return task_id

    async def update(
        self,
        task_id: TaskID,
        total: Optional[float] = None,
        completed: Optional[float] = None,
        advance: Optional[float] = None,
        description: Optional[str] = None,
        visible: bool = True,
        refresh: bool = False,
        filename=None,
        state: Optional[str] = None,
    ) -> None:
        async with self._progress_lock:
            next_state = state or self._progress.tasks[task_id].fields.get("state")
            if visible and next_state not in {
                "completed",
                "skipped",
                "missing",
                "error",
            }:
                for task in self._progress.tasks:
                    if task.id != task_id:
                        self._progress.update(task.id, visible=False)
                self._visible_task_id = task_id

            update_params = {
                key: value
                for key, value in [
                    ("advance", advance),
                    ("description", description),
                    ("state", state),
                    ("filename", filename),
                ]
                if value is not None
            }

            self._progress.update(
                task_id,
                total=total,
                completed=completed,
                visible=visible,
                refresh=refresh,
                **update_params,
            )

            if task_id == self._visible_task_id and (
                not visible
                or next_state in {"completed", "skipped", "missing", "error"}
            ):
                self._visible_task_id = None

            if self._progress.tasks[task_id].finished and task_id in self._active_tasks:
                self._active_tasks.remove(task_id)

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.stop()


class RichConsoleManager(metaclass=Singleton):
    """
    RichConsoleManager 类用于管理进度条和日志输出的控制台，封装了 rich 库中的控制台和进度条功能。

    想要激活进度条，只需使用 with 语句包裹需要显示进度的代码块即可。

    该类利用 Singleton 模式保证只有一个实例，并提供便捷的方法来访问控制台、进度管理器和日志输出。

    类属性:
    - _progress_manager: ProgressManager 实例，用于管理进度条。
    - exception_console: 用于输出异常信息的 Console 实例。
    - rich_console: 主控制台的 Console 实例。
    - rich_prompt: 用于接收用户输入的 Prompt 实例。

    方法:
    - __init__: 初始化 RichConsoleManager 实例，并创建 ProgressManager。
    - progress: 返回 ProgressManager 实例，管理进度条。
    - exception_console: 返回用于输出异常信息的控制台实例。
    - rich_console: 返回主控制台实例，用于显示日志和信息。
    - rich_prompt: 返回用于提示用户输入的 Prompt 实例。

    使用示例:
    ```python
        # 创建 RichConsoleManager 实例
        console_manager = RichConsoleManager()

        # 使用进度条
        with RichConsoleManager.progress:
            task_id = await console_manager.progress.add_task("下载中", total=100)
            await console_manager.progress.update(task_id, completed=50)

        # 输出异常信息
        console_manager.exception_console.print("[bold red]发生错误！[/bold red]")
    ```
    """

    def __init__(self):
        self._console = Console(
            color_system="truecolor",
            theme=CLI_THEME,
            highlight=False,
        )
        self._exception_console = Console(
            color_system="truecolor",
            theme=CLI_THEME,
            stderr=True,
            highlight=False,
        )
        self._prompt = Prompt()
        self._progress_manager = ProgressManager(console=self._console)

    @property
    def progress(self) -> ProgressManager:
        return self._progress_manager

    @property
    def exception_console(self) -> Console:
        return self._exception_console

    @property
    def rich_console(self) -> Console:
        return self._console

    @property
    def rich_prompt(self) -> Prompt:
        return self._prompt

    def line(self, label: str, value: str = "", cursor: bool = False) -> Text:
        return dashboard_line(label, value, cursor)

    def header(self, title: str, cursor: bool = False) -> Text:
        return dashboard_header(title, cursor)

    def rule(self, title: str) -> Text:
        return dashboard_header(title)
