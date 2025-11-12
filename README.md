MofuAssistant

Paper 1.21.4対応のマルチプレイヤーサーバー向けユーティリティプラグインです。

## 機能

### 1. ピースフルモード
プレイヤーごとに敵対Mobから攻撃されない状態を設定できます。

### 2. おすそわ券システム（コミュニティアイテム配布）
LuckPermsグループに基づいてアイテムを自動配布するシステムです。

#### 概要
- LuckPermsの`osusowaken.enable`パーミッションを持つグループに所属するプレイヤーに対して、メンバー数に応じたアイテムを配布します
- 配布は隔週土曜日15時に自動実行、または管理者が手動で開始できます
- メンバーは配布されたアイテムプールから好きな数量を複数回に分けて受け取り可能です

#### 配布数の計算式
- 1人: 160個
- 2人: 256個 (+96個)
- 3人: 320個 (+64個)
- 4～99人: 前の人数 +32個/人
- 100人以降: 前の人数 +16個/人

#### 使用方法

**一般プレイヤー向けコマンド:**
```
/osusowaken              # 配布GUIを開く
/osusowaken info [コミュニティ名]  # コミュニティ情報を表示
/osusowaken status       # 現在の配布サイクル状態を表示
/osusowaken help         # ヘルプを表示
```

**管理者向けコマンド:**
```
/osusowaken setitem      # 手に持っているアイテムを配布アイテムとして設定
/osusowaken start        # 手動で配布サイクルを開始
/osusowaken end          # 配布サイクルを終了
```

#### 受け取り方法
1. `/osusowaken`コマンドでGUIを開く
2. コミュニティを選択（複数所属の場合）
3. 受け取り方法を選択：
   - **全て受け取る**: プールに残っている全てのアイテムを受け取る
   - **個数を指定**: チャットで個数を入力して指定した数量だけ受け取る
4. インベントリに空きがない場合は、入る分だけ受け取られます
5. 残りは後から何度でも受け取り可能です

#### パーミッション
```yaml
mofuassistant.osusowaken:
  description: おすそわ券コマンドの使用を許可
  default: true

mofuassistant.osusowaken.admin:
  description: おすそわ券システムの管理権限
  default: op
```

#### LuckPermsグループ設定例
```bash
# コミュニティグループを作成
lp creategroup setsugen

# 配布対象パーミッションを付与
lp group setsugen permission set osusowaken.enable true

# プレイヤーをグループに追加
lp user プレイヤー名 parent add setsugen
```

#### データベース
- デフォルト: SQLite（`plugins/MofuAssistant/database.db`）
- 対応: MySQL, MariaDB

設定ファイル（`config.yml`）でデータベースタイプを変更できます。

#### 配布サイクル
- 自動配布: 隔週土曜日 15:00 (Asia/Tokyo)
- 配布期間: 開始から次の配布日まで
- 未受取アイテム: サイクル終了時に破棄

#### 技術詳細
- **プールベース配布**: コミュニティごとにアイテムプールを作成し、複数プレイヤーで山分け可能
- **原子的操作**: トランザクションとロックにより同時アクセス時の競合を防止
- **インベントリ対応**: 空き容量に応じて自動調整、入りきらない分は後から受け取り可能
- **サイクル管理**: 配布期間ごとに独立したデータ管理

## インストール

1. [LuckPerms](https://luckperms.net/)をインストール
2. `MofuAssistant.jar`を`plugins`フォルダに配置
3. サーバーを再起動
4. `config.yml`で設定を調整（必要に応じて）

## 設定

`plugins/MofuAssistant/config.yml`:

```yaml
database:
  type: SQLITE  # SQLITE, MYSQL, MARIADB
  address: localhost
  port: 3306
  database: plugins/MofuAssistant/database.db  # SQLiteの場合はファイルパス
  username: root
  password: password
  tablePrefix: ""
```

## 必要環境

- Paper 1.21.4以降
- Java 21以降
- LuckPerms 5.4以降

## アップグレード

### 古いバージョンからのアップグレード

おすそわ券システムを含むバージョンにアップグレードする場合：

1. **データベースのバックアップ**
   ```bash
   # SQLiteの場合
   cp plugins/MofuAssistant/database.db plugins/MofuAssistant/database.db.backup

   # MySQLの場合
   mysqldump -u username -p database_name > backup.sql
   ```

2. **プラグインの更新**
   ```bash
   # 古いjarファイルを削除
   rm plugins/MofuAssistant-old.jar

   # 新しいjarファイルを配置
   cp MofuAssistant-new.jar plugins/MofuAssistant.jar
   ```

3. **サーバーを再起動**
   - プラグインが自動的にデータベーステーブルをマイグレーションします
   - `community_distribution`テーブルに`cycle_id`カラムが追加されます
   - 古い配布履歴データは削除され、新しい構造でテーブルが再作成されます

4. **配布アイテムの設定**
   ```
   /osusowaken setitem
   ```
   手に持っているアイテムを配布アイテムとして設定します。

5. **配布サイクルの開始**
   ```
   /osusowaken start
   ```
   手動で最初の配布サイクルを開始するか、次の隔週土曜日15時まで待ちます。

### 注意事項

- **データ損失**: 古い`community_distribution`テーブルのデータは新しいスキーマと互換性がないため削除されます
- **LuckPerms必須**: おすそわ券システムを使用するにはLuckPerms 5.4以降が必要です
- **初回起動時**: プラグイン起動時にマイグレーション処理が実行されるため、初回起動は若干時間がかかる場合があります

## トラブルシューティング

### データベーステーブルエラー

**エラー**: `no such column: cycle_id`

**解決策**:
1. サーバーを停止
2. `community_distribution`テーブルを削除
   ```sql
   DROP TABLE IF EXISTS community_distribution;
   ```
3. サーバーを再起動（テーブルが自動的に再作成されます）

### プール情報が表示されない

**原因**: 配布サイクルが開始されていない、またはコミュニティにメンバーがいない

**解決策**:
1. `/osusowaken status`で配布サイクルの状態を確認
2. `/osusowaken start`で手動でサイクルを開始
3. LuckPermsでコミュニティグループが正しく設定されているか確認

### 配布アイテムが受け取れない

**チェックリスト**:
- [ ] 配布アイテムが設定されているか（`/osusowaken setitem`）
- [ ] 配布サイクルが有効か（`/osusowaken status`）
- [ ] プールに残量があるか（GUIで確認）
- [ ] インベントリに空きがあるか
- [ ] LuckPermsグループに所属しているか

## ライセンス

Apache License 2.0