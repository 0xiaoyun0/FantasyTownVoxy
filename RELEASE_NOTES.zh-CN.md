# Fantasy Town Voxy 0.0.1

Minecraft **1.21.1 / NeoForge / Java 21** 公开测试版。客户端需要 Sodium 0.8.12；服务端可选，同一个 JAR 可用于两端。替换旧 Voxy / NeoVoxy / HXZ，并移除独立 VSS。

- 修复退出重进后随机更换缓存目录、导致已保存 LOD 看起来丢失的问题。
- 新增 Xaero 小地图、世界地图和路标的后端目录隔离，覆盖同一 Velocity 入口下同种子 A → B → A 的场景。
- 新增每日下载禁止时段、时区、全服和每玩家带宽上限，指令及钠面板均支持在线热更新。
- 新增默认开启的稳定预测、网格逐点采样与冰山/恶地补全，减少低模到精修的山体、水面跳变；修复 RTF 预设初始化缺少噪声设置注册表的问题。三项策略在钠“地形预测”页独立开关，实测定义与结果见仓库 `PREDICTION_ACCURACY.zh-CN.md`。
- 保留原版 NeoVoxy 默认画面、可选 HXZ 优化、RTF 自定义预设、旧 LOD 导入、高地形和负载保护。

设置入口：**钠的视频设置 → Fantasy Town Voxy**，或 `/voxyhxz menu`。管理员进入“管理员 · 下载限制”，先读取服务器配置，需要 OP 2 级。

```text
/voxyhxzserver downloads timezone +08:00
/voxyhxzserver downloads block 17:00 23:00
/voxyhxzserver downloads rate 512 128
```

示例为每天北京时间 17:00–23:00 禁止 LOD 下载，其余时间全服 512 KiB/s、每人 128 KiB/s。默认时段关闭、个人限速 128 KiB/s 开启。`downloads off` 关闭时段限制。原版区块和初次预测参数不计入 LOD 带宽限制。

独立后端需要不同的持久化节点身份，复制配置时不要复用相同 `nodeIdentity`。没有服务端身份的同入口同种子世界无法自动识别。Xaero 隔离默认开启，切换需重进；旧目录保留，不自动混合。已经被覆盖的地图不能自动恢复。旧随机 LOD 数据库可以停机复制后从面板导入，须确认世界与维度。

验证范围和 SHA256 见随附 `validation-summary.json`、`SHA256SUMS.txt` 及仓库 `VALIDATION.md`。包含独立 JVM 重启、真实 Velocity 切服、原生钠面板、下载时段及整合包兼容回归；不代表正式大服长期运行已完成。
