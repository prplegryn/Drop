# F2

`F2` now keeps only DouYin download features, including single video, user posts, liked videos, collections, collection folders, music collections, playlists, live streams, related videos, and friend feeds.

## Usage

```bash
f2 dy -h
f2 dy -u "DouYin URL" -M one
f2 dy -u "DouYin profile URL" -M post
```

The full command name is also available:

```bash
f2 douyin -u "DouYin URL" -M one
```

## Configuration

Default configuration keeps only the `douyin` section:

- `f2/conf/app.yaml`
- `f2/conf/conf.yaml`
- `f2/conf/defaults.yaml`

Notification modules and other platform modules have been removed.
