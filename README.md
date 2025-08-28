# streamChat

Minecraft (PaperMC) 用 Twitch/YouTube チャット連携プラグイン
匿名で Twitch チャット、YouTube 配信チャットを購読し、Minecraft サーバー内のチャット欄に表示します。

---

## 機能

- `/twitch-chat <user_name>` で指定した Twitch 配信者のチャットを購読
- 匿名接続（APIキー不要）
- Minecraft 内に Twitch チャットを自動で流す
- 受信専用（書き込みはできません）
- シンプルなコマンドとタブ補完対応
- `/youtube-chat <user_name>` で指定した YouTube チャンネルの配信チャットを購読（`config.yml` に API キーを設定する必要があります）
- Minecraft 内に YouTube チャットを自動で流す（2秒ごとに取得）

---

## インストール方法

1. [Releases](https://github.com/isksss/streamChat/releases) から最新の `streamChat.jar` をダウンロード
2. サーバーの `plugins/` ディレクトリに配置
3. サーバーを再起動すると有効化されます

---

## 権限

| 権限ノード       | 説明                                    |
|------------------|-----------------------------------------|
| `streamchat.use` | `/twitch-chat` コマンドの利用を許可する |
