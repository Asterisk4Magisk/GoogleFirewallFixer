# GoogleFirewallFixer（RedMagic OS）

一个 Xposed 模块，阻止 RedMagic OS 系统框架在判定 Google 不可用后对 Google 服务的禁网。

> 本分支适用于 RedMagic OS（ZTE，Android 13 及以上），包名 `org.asterisk.zcc.fixer.redmagicos`；ColorOS 版本见 `main` 分支。

## 用途

RedMagic OS 的 system_server 中有 `GoogleOptimizer`：subsys 进程按 SIM 卡或国家码广播 `GMS_AVAILABLE_STATE state=false` 后，它把 `mGmsAllowed` 置为 `false` 并调用 `optimize(0)`，在 iptables 链 `zte_fw_gms` 中 DROP Google Play 服务与 Play 商店的 uid，Google 服务随之无法联网。

本模块：

- 让 `updateCheckStrategy()` 始终走「允许」分支，并把残留的 `optimize(0)` 改为 `optimize(1)`，保持 `zte_fw_gms` 为空；
- 系统启动约 20 秒后主动执行一次 `optimize(1)`，清除模块生效前写入的 DROP 规则；
- 让 SuperKiller 不再查杀 GMS 主进程与 `com.google.android.gms.persistent`（FCM 推送连接所在进程），其他进程不受影响。

模块不改动 `latchsky_enable`、PMS 自身的 `mGmsAllowed` 或其他优化项。

## 使用

在 LSPosed 中启用模块，作用域勾选「系统框架」，然后重启。

## 确认生效

在 LSPosed 日志中搜索 `[GoogleFirewallFixer]`：

- `hooked n/4`：已挂上的 Hook 数量；
- `SuperKiller hooked …`：SuperKiller 实际被 Hook 的方法（Android 13 为 `procSwitchToKillRank`，较新固件为 `getProcessKillRank`）；
- `mGmsAllowed false -> true in updateCheckStrategy`、`optimize(0) -> optimize(1)`：系统试图禁网，已被改为放行；
- `startup optimize(1) done, zte_fw_gms cleared`：启动时已清除残留规则；
- `SuperKiller rank … -> NEVER for …`：GMS 推送进程已免于查杀；
- `GoogleOptimizer not found` 或 `… not found`：当前固件未适配，对应部分未做任何修改。反馈时请附上这段日志与系统版本。

## 构建

```bash
./gradlew :app:assembleRelease
```

## 许可证

GPL-3.0
