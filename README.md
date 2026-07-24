# MuteGuard

被禁言（mute）的玩家，連 `/msg`、`/reply` 這類私訊指令也一併擋下。

## 這是為了解決什麼問題

伺服器用 CMI 管理公開聊天禁言，但另外裝了一個獨立的 `Msg` 插件
（`spigotmc.org/resources/msg-plugin.80931/`）處理私訊。這兩套系統
互不相通：玩家被 CMI 用 `/cmi mute` 禁言後，公開聊天雖然被擋，但
`/msg`、`/reply` 私訊完全不受影響，等於禁言破功。

MuteGuard 在指令真正被任何插件處理**之前**就先攔截，判斷玩家目前
是否處於禁言狀態，是的話直接擋下指令，不讓 Msg 插件有機會執行。

## 怎麼判斷玩家有沒有被禁言

**故意不綁定 CMI 的 API**，而是送出一個模擬的、不會真的廣播出去的
公開聊天事件，看看有沒有任何插件（CMI、EssentialsX，或未來換成任何
其他禁言插件）會把它取消掉。只要負責公開聊天禁言的插件是攔截標準的
聊天事件（幾乎所有禁言類插件都是這樣做），MuteGuard 就抓得到，不用
關心背後實際是哪一套禁言系統，之後就算換插件也不用改 MuteGuard。

## 設定 (`config.yml`)

```yaml
guarded-commands:
  - msg
  - m
  - w
  - tell
  - pm
  - reply
  - r
  - message

mute-message: '&c你目前被禁言中，無法傳送密語。'
```

- `guarded-commands`：要攔截的指令名稱清單（不含斜線），依照你
  `CustomAlias.yml` 裡實際設定的別名去增減。
- `mute-message`：被攔下時顯示給玩家的訊息，支援 `&` 顏色代碼。

## 權限

- `muteguard.bypass`（預設 op）— 就算被禁言，也能繼續使用私訊指令
  （方便管理員／客服帳號不受影響）。

## 編譯

跟 CatchMe 一樣，用 GitHub Actions 自動編譯：把整個資料夾（含
`.github` 資料夾）上傳到一個新的 GitHub 儲存庫，Actions 分頁會自動
跑起來，編譯完成後在 Artifacts 下載 `MuteGuard.jar`。
