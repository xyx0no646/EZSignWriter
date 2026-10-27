# あの***小型電子ペーパーをシンプルに書き換えるヤツ***

EZ Sign / FMSC系のNFC電子ペーパーへ、Androidスマートフォンから画像を書き込むためのシンプルなアプリです。アプリサイズはたったの36KB!

## 主な機能

- NFC（IsoDep）でEZ Signへ直接書き込み
- 2.9インチ（296×128） / 4.2インチ（400×300）に対応
- 2色 / 4色パネルを自動判別
- 画像のドラッグ移動・ピンチ拡大縮小
- 書き込み範囲のプレビュー
- 電子ペーパー向けの仕上がりプレビュー
- 色変換モード（標準 / 擬似青 / 擬似青［パターン］）
- 左右反転補正
- 日本語UI
- Androidの「共有／送る」から画像を直接読み込み
- インターネット権限なし
- WebView / Capacitor / AndroidX / 外部ランタイム依存なし

## 簡単な使い方

1. スマートフォンの **NFCをオン** にしてアプリを起動します。
2. **「画像を選択」** で写真などを開きます。写真アプリなどから **「共有／送る」→「EZ Sign ライター」** を選んでも読み込めます。
3. **「2.9インチ／4.2インチ」** で使う製品のサイズを選び、指でドラッグして画像の位置を調整します。ピンチで拡大縮小、ダブルタップまたは **「リセット」** で初期位置に戻せます。
4. **「色変換モード」** で「標準」「擬似青」「擬似青（パターン）」を選びます。青を物理的に表示できないため、擬似青では青系を黒白で強調・模様化します。**「仕上がり」** で電子ペーパー向けに減色した結果を確認します。**「編集」** で調整画面に戻ります。
5. 必要なら **「左右反転補正」** を切り替えて **「NFCに書き込む」** を押します。
6. 電子ペーパーをスマートフォンのNFC読み取り位置にかざし、**完了の表示が出るまで離さないでください。**

**補足:** 実機のサイズと選択中のプレビュー枠が違う場合は、正しいサイズに切り替えて書き込みを中断します。構図を確認してから再度書き込んでください。

## APKのダウンロード

**[最新APKを直接ダウンロード](https://github.com/xyx0no646/EZSignWriter/releases/latest/download/EZSignWriter.apk)**

[ダウンロードページ（GitHub Releases）](https://github.com/xyx0no646/EZSignWriter/releases/latest)

GitHub Actionsで生成したデバッグ署名版APKを、Releasesに自動アップロードしています。スマートフォンから上記リンクを開き、ダウンロードした `EZSignWriter.apk` をインストールしてください。

ビルド履歴やZIP形式の成果物は [GitHub Actions](https://github.com/xyx0no646/EZSignWriter/actions/workflows/build-apk.yml) でも確認できます。

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
