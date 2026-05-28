# F2 抖音下载工具

这个版本只保留抖音下载相关功能。可下载单个作品、主页作品、点赞作品、收藏作品、收藏夹作品、收藏音乐、合集、直播、相关推荐和朋友作品等内容。

通知模块和其他平台模块已移除。

## 运行方式

在项目目录中，如果还没有安装命令行入口，可以直接用：

```bash
python -m f2 dy -h
```

安装到当前 Python 环境后，可以用 `f2` 命令：

```bash
pip install -e .
f2 dy -h
```

`dy` 是 `douyin` 的简写，下面两种写法等价：

```bash
f2 dy -u "抖音链接" -M one
f2 douyin -u "抖音链接" -M one
```

## 基本命令

命令格式：

```bash
f2 dy -u "链接" -M "模式" [其他参数]
```

最常用参数：

| 参数 | 说明 |
| --- | --- |
| `-u, --url` | 抖音作品、主页、合集或直播链接 |
| `-M, --mode` | 下载模式 |
| `-p, --path` | 保存目录，默认 `Download` |
| `-k, --cookie` | 登录后的 Cookie |
| `-o, --max-counts` | 最大下载数量，`0` 表示不限制 |
| `-s, --page-counts` | 每页拉取数量，默认建议不超过 `20` |
| `-t, --max-tasks` | 下载任务并发数 |
| `-x, --max-connections` | 网络并发连接数 |
| `-e, --timeout` | 请求超时时间 |
| `-n, --naming` | 文件命名格式 |
| `-m, --music` | 是否保存视频原声，传 `true` 或 `false` |
| `-v, --cover` | 是否保存封面，传 `true` 或 `false` |
| `-d, --desc` | 是否保存文案，传 `true` 或 `false` |
| `-L, --lyric` | 是否保存歌词，传 `true` 或 `false` |

查看完整帮助：

```bash
f2 dy -h
```

## 下载模式

| 模式 | 用途 | 链接类型 |
| --- | --- | --- |
| `one` | 下载单个作品 | 作品链接 |
| `post` | 下载用户主页作品 | 用户主页链接 |
| `like` | 下载点赞作品 | 用户主页链接 |
| `collection` | 下载收藏作品 | 用户主页链接，通常需要 Cookie |
| `collects` | 下载收藏夹作品 | 收藏夹链接或用户主页链接 |
| `music` | 下载收藏音乐 | 用户主页链接，通常需要 Cookie |
| `mix` | 下载合集作品 | 合集链接 |
| `live` | 下载直播流 | 直播间链接 |
| `related` | 下载相关推荐作品 | 作品链接 |
| `friend` | 下载朋友作品 | 需要登录 Cookie |

## 常用示例

下载单个作品：

```bash
f2 dy -u "https://www.douyin.com/video/作品ID" -M one
```

下载某个用户主页作品，最多下载 30 个：

```bash
f2 dy -u "https://www.douyin.com/user/用户ID" -M post -o 30
```

下载点赞作品：

```bash
f2 dy -u "https://www.douyin.com/user/用户ID" -M like -o 30
```

下载收藏作品，建议带登录 Cookie：

```bash
f2 dy -u "https://www.douyin.com/user/用户ID" -M collection -k "你的 Cookie"
```

下载合集：

```bash
f2 dy -u "https://www.douyin.com/collection/合集ID" -M mix
```

下载直播：

```bash
f2 dy -u "https://live.douyin.com/直播间ID" -M live
```

指定保存目录：

```bash
f2 dy -u "抖音链接" -M one -p "./Download"
```

自定义文件名：

```bash
f2 dy -u "抖音链接" -M one -n "{create}_{desc}"
```

支持的命名字段：

- `{nickname}` 作者昵称
- `{create}` 发布时间
- `{aweme_id}` 作品 ID
- `{desc}` 作品文案
- `{uid}` 作者 ID

## Cookie

公开作品通常不需要 Cookie。下载自己的收藏、点赞、朋友作品，或遇到接口限制时，建议使用登录后的 Cookie。

手动传 Cookie：

```bash
f2 dy -u "抖音链接" -M post -k "你的 Cookie"
```

自动从浏览器读取 Cookie：

```bash
f2 dy --auto-cookie chrome
```

支持的浏览器包括 `chrome`、`edge`、`firefox`、`brave`、`chromium`、`opera`、`vivaldi`、`librewolf` 等。使用前需要关闭对应浏览器，否则可能读取失败。

## 配置文件

默认配置文件只保留 `douyin` 节点：

- `f2/conf/app.yaml`：常用默认参数，例如保存路径、Cookie、超时和并发。
- `f2/conf/conf.yaml`：请求头、代理、算法和直播 WSS 等低频配置。
- `f2/conf/defaults.yaml`：生成自定义配置时使用的模板。

生成一个自定义配置文件：

```bash
f2 dy --init-config my-douyin.yaml
```

使用自定义配置文件运行：

```bash
f2 dy -c my-douyin.yaml
```

更新自定义配置文件：

```bash
f2 dy -c my-douyin.yaml --update-config -u "抖音链接" -M post -p "./Download"
```

一个简单的自定义配置示例：

```yaml
douyin:
  url: "抖音主页链接"
  mode: post
  path: Download
  naming: "{create}_{desc}"
  cookie: "你的 Cookie"
  max_counts: 30
  page_counts: 20
  max_tasks: 10
```

## 代理

如果网络环境需要代理，可以这样传入：

```bash
f2 dy -u "抖音链接" -M one -P http://127.0.0.1:7890 http://127.0.0.1:7890
```

两个代理参数分别对应 `http://` 和 `https://`。

## 注意事项

- 链接和模式必须匹配，例如单作品用 `one`，主页用 `post`，直播间用 `live`。
- `collection`、`music`、`friend` 等模式通常需要登录 Cookie。
- `max_counts` 为 `0` 表示不限制数量，批量下载时建议先设置一个较小数量测试。
- 如果命令行里没有传某个参数，会从配置文件读取默认值。
