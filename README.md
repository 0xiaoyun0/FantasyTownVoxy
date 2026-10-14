# Fantasy Town Voxy 0.0.1

Minecraft **1.21.1 / NeoForge / Java 21** 的远景整合模组，将所提供的 NeoVoxy 0.6.1 与 VSS 0.3.5 合并为同一个 JAR。客户端可独立使用，服务端安装可选；共享服务器已经生成的地形需要两端安装。

本次公开测试版按项目要求重新编号为 **0.0.1**，接续之前的 0.2 本地测试版本。运行时直接使用 NeoForge，不需要 Fabric 或 Connector。构建通过 Java 源码覆盖与 ASM 修改指定上游字节码，详见 [构建说明](BUILDING.md) 和 [组件来源](THIRD_PARTY_NOTICES.md)。

## 下载与安装

从 [Releases](https://github.com/0xiaoyun0/FantasyTownVoxy/releases) 下载 `fantasy-town-voxy-0.0.1-neoforge-mc1.21.1.jar`，放入 `mods`，替换旧 Voxy / NeoVoxy / VOXY-HXZ，并移除独立 VSS。不要重复安装。

- 客户端需要 **Sodium 0.8.12**；已在 NeoForge 21.1.250 上测试。
- 服务端不需要 Sodium 或 Iris；已在 NeoForge 21.1.236 上测试。
- RTF / NeoTerraForged、Xaero 和航空学仍需按整合包需要独立安装。
- 新配置默认 **NeoVoxy 原版画面 + 渐进性能调度**；HXZ 画面优化可选，各项策略可单独开关。

## 本版重点

- **重进后读回已保存 LOD**：无服务端身份时使用稳定地址作用域，消除每次连接随机生成目录导致的“数据丢失”。
- **Velocity 跨服 Xaero 隔离**：小地图、世界地图及路标按后端身份保存；同入口同种子的 A → B → A 会复用 A 的目录。
- **管理员下载限制**：每日禁止时段、时区、全服总带宽和每玩家上限，在线应用后生效。
- **稳定预测**：低模与精修共用地形采样，修复 RTF 自定义预设初始化，严格网格采样和稳定预览均可在钠面板关闭；[实测方法与结果](PREDICTION_ACCURACY.zh-CN.md)。
- **统一钠面板**：14 个中文分类，包含显示、性能、共享、RTF 预测、旧 LOD 导入和 5 类管理员功能。
- **保留高地形与负载修复**：支持最多 255 个合法垂直区段，限制请求、队列、后台任务与缓存，保留服务端异常暂停与恢复。
- **兼容策略**：保留 RTF 自定义预设适配、按预设隔离预测缓存、拒绝过期切服数据，以及可选的 Sable 船体存储区过滤。

## 游戏内设置

进入 **选项 → 视频设置（钠）→ Fantasy Town Voxy**，或输入 `/voxyhxz menu`。修改后点击“应用”，右键数值项可精确输入。管理员先读取服务器配置，需要 **OP 2 级**。

下载限制位于 **管理员 · 下载限制**，也可用指令：

```text
/voxyhxzserver downloads timezone +08:00
/voxyhxzserver downloads block 17:00 23:00
/voxyhxzserver downloads rate 512 128
/voxyhxzserver downloads
```

例子表示北京时间每天 **17:00（含）至 23:00（不含）** 暂停下载，其余时间全服最多 512 KiB/s、每人最多 128 KiB/s。`downloads off` 关闭时段限制，`downloads player-limit-off` 关闭额外的个人上限，`sharing off` 关闭整个服务器 LOD 共享。

默认**时段限制关闭**（预填 17:00–23:00、UTC+08:00），**每人限速开启，为 128 KiB/s**；全服默认 500000 B/s。限制的是 VSS LOD 列数据及请求确认，不限制原版区块、聊天或初次地形预测参数同步。单个大包需要积累额度，瞬时流量可能波动。禁止下载期间已保存的本地远景仍可显示；恢复时段后在线客户端重新协商，无需重进。跨午夜时段受支持，起止相同表示全天禁止。时区为固定 UTC 偏移，不自动跟随夏令时。

## 多后端与旧数据

“数据与兼容 → Xaero 跨服地图隔离”默认开启，修改后需重新进入服务器。独立后端都应安装本模组并保持不同的持久化 `nodeIdentity`，`sharedWorld` 保持 `false`。新安装会生成身份；复制服务端配置时不要复制成相同节点身份。身份变更需要重启后端。

如果服务端不提供身份，同一个代理入口下、相同种子的独立世界无法可靠区分。此时需要服务端身份支持，或在切服前设置不同的手动后端标识并重新连接。

本版不删除旧 Xaero 地图或旧 LOD 库。Xaero 开启隔离后使用新目录，已有地图不会自动猜测所属后端并合并；关闭该开关后重进可回到旧目录。已经被覆盖的旧地图数据无法凭空恢复。旧 Voxy 数据可在“导入 LOD”选择具体世界、维度的数据库；当前客户端正在管理的缓存树应先停机复制到外部目录再导入。源库只读，目标同坐标数据会被替换。

## 说明与验证

- [快速使用](QUICKSTART.zh-CN.md) · [完整功能与导入说明](README_HXZ.zh-CN.md)
- [客户端选项](SETTINGS.zh-CN.md) · [服务端保护](SERVER_SAFETY.zh-CN.md)
- [本次更新](CHANGELOG.md) · [验证记录与限制](VALIDATION.md)
- [构建说明](BUILDING.md) · [第三方声明](THIRD_PARTY_NOTICES.md)

30 FPS 是本模组的调度目标，不是对硬件、光影或其他模组的帧率保证。短时隔离测试覆盖了真实代理切服、独立 JVM 重启、下载策略及整合包启动，不代替大型正式服的长期负载测试。
