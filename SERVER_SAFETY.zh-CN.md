# 服务端防护 · Fantasy Town Voxy 0.0.2

## 下载时段和带宽

此策略独立于负载保护总开关；关闭 CPU 防护不会取消下载限制。在钠“管理员 · 下载限制”读取服务器值并应用。

| 设置 | 默认 | 作用 |
|---|---:|---|
| `downloadScheduleEnabled` | false | 启用每日禁止下载时段 |
| `downloadBlockStartHour` / `downloadBlockStartMinute` | 17 / 0 | 开始时刻（含），按指定时区 |
| `downloadBlockEndHour` / `downloadBlockEndMinute` | 23 / 0 | 结束时刻（不含）；支持跨午夜，相同起止表示全天禁止 |
| `downloadUtcOffsetMinutes` | 480 | 固定 UTC 偏移分钟，-720 至 840；北京时间 +480 |
| `downloadLimitPerPlayer` | true | 启用每玩家额外上限 |
| `downloadPlayerBytesPerSecond` | 131072 | 每人 B/s；范围 1024 至 12500000 |
| VSS `totalBandwidthBytesPerSecond` | 500000 | 所有玩家合计 B/s；范围 62500 至 12500000 |

禁时段内服务器拒收新的区块请求，并暂停发送排队的 LOD；客户端已保存的远景不删除。恢复时段后向在线兼容客户端重新协商。全服和个人额度同时取得后才发送，不睡眠阻塞线程、不新增负载队列。大包需要先累积额度，小确认包不会抹掉它正在积累的额度；允许单包和短突发级别的流量波动。

只计量 VSS LOD 列和请求确认，原版区块、聊天和初次地形预测参数不受此限制。时区不自动跟随夏令时。操作需要服务端 OP 2 权限，应用和命令均会保存并热更新。指令示例见 [README](README.md)。

## 负载保护

服务端保护默认开启。管理员可在 `/voxyhxz menu` 打开的钠面板内，进入“管理员”分类，先读取服务器配置，再修改并点击钠的“应用”，服务器核验 OP 2 权限；普通客户端设置不能覆盖这些值。旧 `vss-server-config.json` 保留原值，防护另存 `config/voxy-hxz-server.json`。

面板“重载配置”或 `/voxyhxzserver reload` 从文件热重载两份配置；格式错误时保留当前运行设置。服务器身份、节点身份和共享世界身份关系改变需要重启，不允许在线混用缓存。

查看运行状态：`/voxyhxzserver safety status`。修改格式：`/voxyhxzserver safety set <名称> <true/false 或整数>`；省略数值可读取当前设置，Tab 可补全。设置立即保存；正在执行的任务会自然结束，缩小限制不会强行中断它们。

| 设置 | 默认 | 可选范围/作用 |
|---|---:|---|
| `enabled` | true | 防护总开关；也可用 `safety on` / `safety off` |
| `limitRequests` | true | 网络入口有界排队、去重、玩家速率和公平分批；关闭恢复原主线程提交方式 |
| `tickBudget` | true | 工作单元之间检查主线程耗时预算 |
| `pauseOnOverload` | true | 根据完整 tick 耗时及堆占用暂停新请求 |
| `limitResources` | true | 对 VSS 原配置施加实际队列、线程、发送和缓存上限 |
| `isolateErrors` | true | 普通运行异常暂停 LOD 服务并记录堆栈 |
| `allowLodGeneration` | false | 是否允许 LOD 请求触发新地形；同时需要 `/vss generation enable` |
| `backgroundPreload` | false | 服务端主动扫描/推送持久缓存；关闭时仍响应已保存地形请求 |
| `filterSablePlots` | true | 排除 Sable 船体存储区请求，独立于防护总开关；在“负载保护”页 |
| `cacheProfileTransfers` | true | 多人进入同一地形时复用参数编码，独立于防护总开关；在“传输与缓存”页 |
| `profileCacheMiB` | 16 | 4–64，地形参数编码缓存预算；最多 4 份编码，超限单份不保留 |
| `columnsPerTick` | 16 | 1–128，全服每 tick 处理请求列数 |
| `columnsPerPlayerTick` | 4 | 1–32，每玩家每 tick 列数；每秒准入上限为此数的 20 倍 |
| `queuedColumns` | 512 | 16–4096，全服等待列数 |
| `queuedColumnsPerPlayer` | 64 | 8–512，每玩家等待列数；也限制其 VSS 发送队列条数 |
| `tickBudgetMillis` | 4 | 1–20，LOD 主线程协作调度预算，毫秒 |
| `pauseAboveMillis` | 80 | 50–1000，连续三次慢 tick 触发暂停的阈值 |
| `heapPercent` | 85 | 50–95，堆内存压力阈值 |
| `cooldownSeconds` | 5 | 1–60，负载恢复前暂停时长；仍高负载会续期 |
| `diskTasks` | 64 | 4–256，单类磁盘任务（含所谓无限制路径/续接任务）的上限 |
| `workerThreads` | 2 | 1–8，磁盘及地形打包线程的有效上限 |
| `completionTasks` | 32 | 4–128，等候服务器主线程执行的读盘完成回调数 |
| `sendPackets` | 8 | 1–32，全服每 tick 发送列数据包的有效上限 |
| `sendQueueMiB` | 8 | 1–64，每玩家发送数据队列预算 |
| `cacheMiB` | 32 | 4–256，VSS 内存列缓存预算 |
| `packingMiB` | 8 | 1–64，待打包区块快照预算 |

为了让握手不被大摘要挤占，普通控制消息队列最多 128 条、每连接 8 条；压缩区域摘要单独最多 2 条。重复握手最短间隔 5 秒。排队列和摘要超过 10 秒会丢弃，断线或换维度时移除其等待任务；同 ID 重复请求不会取消原来已接受的请求。以上属于 `limitRequests` 保护的一部分。

`limitResources` 还限制原 VSS 的一些大默认值：每玩家新地形生成并发最多 1、全服最多 2；自动每 tick 新启动/完成的生成任务最多 1；完成预算最多 1 ms；缓存最多 2048 列、脏版本最多 25000 条、持久缓存索引最多 100000 条。这些是有效上限，原 VSS 设置较低时保留较低值。关闭该开关可恢复原 VSS 资源策略。

状态字段：`queued` 等待请求列数、`completions` 等待回调数、`accepted/rejected` 累计准入/拒绝数量、`paused` 负载暂停、`circuit` 错误暂停、`observedTick` 观测耗时、`maxLodStep` 最慢单次 LOD 操作、`last` 最近故障阶段。计数包含正常客户端重试，拒绝数量不代表玩家被踢出。

发生普通 LOD 异常时，日志保留堆栈，LOD 工作暂停，后续 tick 清理生成任务及请求状态。确认原因后执行 `/voxyhxzserver safety reset` 恢复。停用共享仍使用 `/voxyhxzserver sharing off`。这些操作不删除世界或 LOD 数据库。

主线程预算不是抢占式超时。单次世界生成资料序列化、存盘操作、其他模组的调用可能耗时更久；Java 无法安全强杀该操作后继续使用其对象。这里不修改 Minecraft watchdog，也不承诺防止 JVM/原生库崩溃、其他模组死锁、系统内存耗尽或全部硬件故障。RTF 客制化预设同步保留，默认关闭的是服务器按 LOD 请求创建新地形。

编码缓存仅复用同一份不可变地形参数，发送编号及包数组每次独立。参数版本改变会重新编码，停服清空；关闭开关或缩小容量后通过现有应用/重载流程释放多余缓存。该预算只计缓存编码字节，不是整个进服流程的内存上限，也不消除首次地形参数采集耗时。

复现入口：`python tools/server_guard_tests.py --baseline` 会在原始 VSS 上失败；去掉 `--baseline` 验证修复。`python tools/validate_release.py` 启动本地隔离环境，包含 16384 个请求的真实联机洪峰、异常恢复、旧配置有效上限、慢 tick 注入和其他兼容场景。
