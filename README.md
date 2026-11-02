# GoogleFirewallFixer

一个 Xposed 模块，阻止 ColorOS 电池组件在 Google 连通性探测失败后对 Google 服务的禁网。

## 用途

ColorOS 16 / 17 国行固件的电池组件（`com.oplus.battery`）会在开机后和网络变化时探测 Google 是否可达。探测失败时，它把 Google Play 服务、Google Play 商店和 ConfigUpdater 的联网策略写成「禁止全部联网」（同时禁止 Wi-Fi 和移动数据）。

- VPN 模式下，探测会因为检测到 VPN 而直接判定成功，不受影响；
- ROOT 透明代理模式没有 VPN，探测往往在代理就绪前直连失败，Google 服务随之无法联网。

本模块让电池组件始终按「未限制」处理这几个包：它会把已写入的禁网策略改回不限制，并不再写入新的禁网。

## 确认生效

在 LSPosed 日志中搜索 `[GoogleFirewallFixer]`：

- `hooked inform=… control=… battery=…`：已成功挂上，并记录了电池组件中实际被 Hook 的方法名与电池版本；
- `restricted=true -> false in …`：电池组件试图禁网，已被改为不限制；
- `… method not unique: …` 或 `GoogleRestrictionController not found`：当前电池版本未适配，模块未做任何修改。反馈时请附上这段日志与电池版本。

## 构建

```bash
./gradlew :app:assembleRelease
```

## 许可证

GPL-3.0
