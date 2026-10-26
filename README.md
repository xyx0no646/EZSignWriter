# EZ Sign Writer

EZ Sign / FMSC系のNFC電子ペーパーへ、Androidスマートフォンから画像を書き込むためのシンプルなアプリです。

## 主な機能

- NFC（IsoDep）でEZ Signへ直接書き込み
- 2.9インチ（296×128） / 4.2インチ（400×300）に対応
- 2色 / 4色パネルを自動判別
- 画像のドラッグ移動・ピンチ拡大縮小
- 書き込み範囲のプレビュー
- 電子ペーパー向けの仕上がりプレビュー
- 左右反転補正
- 日本語UI
- インターネット権限なし
- WebView / Capacitor / AndroidX / 外部ランタイム依存なし

## APKのダウンロード

GitHub Actionsで毎回デバッグAPKを自動ビルドしています。

[APKビルド / ダウンロードページ](https://github.com/xyx0no646/EZSignWriter/actions/workflows/build-apk.yml)

ビルドが成功した実行を開き、ページ下部の **Artifacts** から `EZSignWriter-debug-apk` をダウンロードしてください。

> このリポジトリはPrivateのため、APKのダウンロードにはこのリポジトリへアクセスできるGitHubアカウントが必要です。

## ビルド

必要環境:

- Java 17
- Gradle 8.9
- Android SDK 35

ビルドコマンド:

```sh
gradle --no-daemon assembleDebug
```

生成されるAPK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 参考

このプロジェクトの画像変換・NFC電子ペーパーへの書き込み方式を調査・実装するにあたり、以下のプロジェクトを参考にしました。

- [redphx/dithr-beta](https://github.com/redphx/dithr-beta)

本プロジェクトは独立したJava実装です。

## ライセンス

MIT Licenseです。詳しくは [LICENSE](LICENSE) を参照してください。
