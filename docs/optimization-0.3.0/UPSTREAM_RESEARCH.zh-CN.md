# 0.3.0 上游实现调研与优化取舍

调研日期：2026-10-19。目标：Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21。上游对照起点为本仓库 `beb9cb4`（0.0.3）；下节另列 0.3.0 工作树中实际采用的范围。本文是设计依据，不是完整性能测试报告；本调研工作没有启动游戏、服务器或修改用户配置。

本次先核对了 `THIRD_PARTY_NOTICES.md` 与 `OPTIMIZATION_REVIEW.zh-CN.md`，再读取上游固定提交的源码。最值得做的是减少重复摄入和读取、把已知真实地形尽快交给渲染器、让请求速度服从客户端实际消费能力；单纯增加预测距离或线程数不能证明质量与速度同时提高。

## 0.3.0 实际采用范围

以下是本轮源码状态，不代表五项建议已经全部实施，也不代表已经移植了最新版 NeoVoxy、LSS、DH 或 C2ME。最终构建、原生 Mixin 和 RTF 场景结果以对应验证日志为准。

| 方向 | 当前 0.3.0 已实现的范围 | 仍属后续项 |
|---|---|---|
| 摄入与保存 | 下载侧加入实际 Voxy 摄入队列反压；保留既有版本/会话校验和完整列，沿用原摄入与保存链。 | 未替换 Voxy 体素摄入队列，未实现逐世界/逐区段快照合并、摄入硬字节配额或新的世界引用生命周期。 |
| 可见精细工作优先 | `PredictionQualityWorkMixin` 只让规划器已选择的完整细叶子跳过隐藏父层，按近处与完成度优先，保留 CPU/帧/线程/内存许可；有独立开关。`PredictionPressureRetirementMixin` 补充压力下过期远处无父精细缓存的释放。 | 未重写真实 Voxy GPU 建网队列，未引入 DH 调度器；没有承诺整个远景范围都达到 1–2 方块精度，也没有放开粗预测显示。 |
| 下载跟随消费速度 | `LodIngestBudget` / `ClientLodBackpressure` 同时检查实际摄入区段、解码列数及字节；停止线与有效 service 线程有关，恢复采用低水位。完整列等待期间维持其原请求资格，未回复请求继续超时。 | 未移植 LSS 新网络协议、地图消费者 watchdog/自动摘除机制，也未按 GPU 上传压力调节带宽；当前不是总堆内存的硬上限。 |
| 服务端异步读取 | `DiskTaskRuntime` / `TrackedTaskExecutor` 修正已预留异步读取继续执行时重复争抢许可、以及关闭/拒绝路径的回收；保持原有读取共享、时间窗和限速。 | 未新增 LSS 式 I/O 延迟 EWMA/AIMD；不能把原已存在的共享读取统计算成本轮新优化。 |
| 网格质量 | 沿用精细质量筛选、原生网格/材质与采样格式，增加实际父层缺失、内存恢复及 RTF 进度验证。 | 未复制上游新的 Mipper/水面规则；水岸、薄墙、透明材质和光影的全面画质量测仍需单独场景证据。 |

客户端反压的可复现问题是：旧处理器在实际 `VoxelIngestService` 排队 8192 个区段时仍取走下一完整列；新门槛下队列、计数与字节保持，积压回落后恢复同一优先次序。`tools/ingest_backpressure_test.py --baseline` 保留该失败反馈，`--sources` 检查真实处理器与纯策略。区段门槛为 256–1024 项，按有效 service 线程数计算；区段快照大小可变，最后一个完整列及原版摄入可造成暂时越线，不能换算成固定 128 MiB 堆上限。

长暂停的单独反例显示：完整列已收到并等待 180 秒，原请求超时后同位置重请求会令原列 `STALE`、摄入回调次数为 0。现在 timeout sweep 只刷新当前会话完整队列中 requestId/位置仍匹配的请求；同一请求最多每秒刷新一次，未收到的请求仍正常超时。`tools/ingest_timeout_test.py` 使用真实 tracker 与 manager，修复后同一列为 `APPLIED` 且回调恰好一次；此项不假称已经模拟完整数据库断电恢复。

父层依赖审查区分两套渲染器：VSS 独立预测网格的实际驻留快照/所有权探针可在无父层时选择完整 4096 格，这不等于修改真实 Voxy 八叉树的层级发现规则。原 VSS 回收策略仍要求祖先已驻留，导致远处无父细缓存在持续堆压力下等待一个无法取得 workspace 的父构建。纯 Java 反例使用真实 adaptive budget、真实 manager，模拟 120 秒后仍无进展；新压力回收只允许不再 desired、没有 pending、超出 horizon 加 1024 格退休边距的 LOD 0/1 缓存，包含完成的 64 格网格和被画质门槛隐藏的旧 16/32 格中间缓存。沿用 `removeTile` 释放内存许可、surface/stored 标记和所有权 epoch，磁盘缓存与真实 LOD 不删除。检查还覆盖 GPU 驻留引用移除、模拟 GC 后重新获得 workspace，以及恢复后 planner 不再提前返回。`tools/prediction_pressure_test.py --baseline` 是红反馈，无参以完全对应的两处调用重定向运行无游戏合同；真实 Mixin 另由 `PredictionPressureProbe` 在原生测试运行，不能混称二者。

## 版本与来源边界

| 项目 | 本次实际读取的固定版本 | 对当前目标的意义 |
|---|---|---|
| NeoVoxy multiversion | `46a0962f7dc5fefe69974d806e24852a18ba0ed5`，2026-10-07 | 主目录为 MC 1.21.1，但构建依赖是 NeoForge **21.1.217**，版本标为 0.5.0-alpha.12；不能将它的结果当作 21.1.250 验证。[构建属性](https://github.com/NHblock-Johnsnow/neo-voxy-multiversion/blob/46a0962f7dc5fefe69974d806e24852a18ba0ed5/gradle.properties) |
| Voxy dev | `534d58ec8b4aa412ef314b884295552c69d480a6`，2026-09-14 | 当前分支是 MC **26.2 / Fabric**，只借鉴内部机制，不直接搬映射、Sodium 或渲染 API。[构建属性](https://github.com/MCRcortex/voxy/blob/534d58ec8b4aa412ef314b884295552c69d480a6/gradle.properties) |
| LOD Server Support（LSS） | `178d9cd4411ba9e3423e0278d6a39bf755db171a`，2026-10-14 | 与用户提供的 VSS 0.3.5 是不同代码代际；1.21.1 NeoForge 维护等级仍为 best-effort，其新接口和协议不能假定已经在本整合中存在。[兼容说明](https://github.com/VoX/lod-server-support/blob/178d9cd4411ba9e3423e0278d6a39bf755db171a/README.md) |
| Distant Horizons | 主仓库 `6763cc8a6f8e96bc59e258bf48d96707b41842b3`；关联 core 子模块 `8d5aa7c19bf95616c57a80e1d04a7e1da35ce2ca` | 保留 1.21.1 NeoForge 构建，属性指定 21.1.228；用于参考调度分层，不合并第二套 LOD 渲染器。[1.21.1 属性](https://gitlab.com/distant-horizons-team/distant-horizons/-/blob/6763cc8a6f8e96bc59e258bf48d96707b41842b3/versionProperties/1.21.1.properties)、[core 子模块](https://gitlab.com/distant-horizons-team/distant-horizons/-/tree/6763cc8a6f8e96bc59e258bf48d96707b41842b3/coreSubProjects) |
| C2ME NeoForge | `backports/1.21.1` 的 `13f9747dcbf92d3244426382f7b830f9197c15b8`，2026-10-11 | 默认分支不是 1.21.1，必须选 backports；它是含多层上游与补丁的工程，不能从默认 README 推导当前整合包线程安全。[版本树](https://github.com/RelativityMC/C2ME-neoforge/tree/13f9747dcbf92d3244426382f7b830f9197c15b8)、[子模块](https://github.com/RelativityMC/C2ME-neoforge/blob/13f9747dcbf92d3244426382f7b830f9197c15b8/.gitmodules) |

用户提供的 NeoVoxy 0.6.1 二进制与上述另一个维护者的 0.5.0-alpha.12 不应按版本数字大小判断新旧或兼容性，仍以本仓库已记录的输入 SHA256 为准。

## 与 0.0.3 对照得到的不足

1. **显示门槛不等于生成加速。**0.0.3 的 `PredictionQualityGate` 只允许完整 64×64 网格、采样间距 1–2 格进入快照，并一起过滤所有权；这解决粗预测占位，但不会自动减少幕后粗层构建，也不会使远处所有区域具备同样精度。继续加大全景预测距离可能主要增加不可见工作。应衡量“真正可显示的精细数据完成时间”，不能以原始 ready tile 数量表示进度。[基线门槛](https://github.com/0xiaoyun0/FantasyTownVoxy/blob/beb9cb4/src/main/java/dev/xantha/vss/client/prediction/PredictionQualityGate.java)
2. **本地原始摄入链有无界队列。**用户提供二进制的本地反编译 `build/baseline-src/.../VoxelIngestService.java` 使用 `ConcurrentLinkedDeque`。上游新 NeoVoxy 也没有由队列类型提供硬性容量限制。因此解码很快、体素化很慢时，“网络队列空了”不代表可以继续全速接收。此项是代码结构风险，未在本轮声称已经复现 OOM。
3. **网格排队尚不能证明可见近处优先。**基线和所读 NeoVoxy 的 `RenderGenerationService` 按层级、重试次数、追加标记和序号计算优先级，未把相机距离写进该函数。任务按区段位置去重，缺模型会请求烘焙并重试。优化必须保留这些正确性机制，不能把全部工作简单换成距离优先队列。[NeoVoxy 对应实现](https://github.com/NHblock-Johnsnow/neo-voxy-multiversion/blob/46a0962f7dc5fefe69974d806e24852a18ba0ed5/src/main/java/me/cortex/voxy/client/core/rendering/building/RenderGenerationService.java#L27-L53)
4. **服务器读取速度与绘制速度需要分别量度。**已有公平排队、带宽限速和缓存不能证明客户端无积压，也不能证明缓慢磁盘下普通游戏区块加载不受影响。测试必须覆盖服务端读取、传输、客户端摄入、网格到可见四段，保留所有时间窗与每玩家限速。

## 优先实施的五项方案

### 1. 真实区段摄入合并，保留数据版本和生命周期

**源码证据：**NeoVoxy 摄入为每个排队任务保留 `WorldEngine` 引用，处理 `finally` 和停服排空时释放，避免长队列期间世界被闲置清理提前关闭；体素临时区段通过 `ThreadLocal` 复用。其队列仍无硬容量，因此只能参考引用生命周期，不能把新版整体替换当作背压修复。[VoxelIngestService](https://github.com/NHblock-Johnsnow/neo-voxy-multiversion/blob/46a0962f7dc5fefe69974d806e24852a18ba0ed5/src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java#L28-L95)

**建议：**以“具体世界实例/会话 + 区段坐标”合并尚未处理的重复更新，保留最后到达的有效快照；合并前必须确认消息是否带内容版本，不能把乱序旧版本当作最新。正在转换的任务允许完成，其后的更新合并为一次补做。空气清除和光照更新也必须到达，不能只保留非空气。队列按项目数和估算字节双重计量；受限时停止上游新请求，保留待重试状态，不能静默丢弃仍标记“已保存”的数据。世界引用在替换、失败、取消、关闭路径均精确释放一次。

**验收指标：**同一区段连续 N 次更新的转换次数；最终体素、光照与顺序输入参考结果完全相同；空气清除保留；A→B→A 切服错误写入数为 0；关闭后遗留任务和世界引用为 0；峰值队列项目数/字节；真实区段每秒完成量及 p95 摄入延迟。与 0.0.3 同输入对照，不能只比较任务提交量。

### 2. 优先呈现可用真数据，将精修完成时间作为调度目标

**源码证据：**DH 将 `Render Loader` 设为较高优先级，`LOD Builder` 积压达到“线程数×200”时暂停新 worldgen；高速移动时可暂停非必要的生成/传播，减少永远追不上的工作。该数字属于 DH，不应原样成为 Voxy 默认值。[ThreadPoolUtil](https://gitlab.com/jeseibel/distant-horizons-core/-/blob/8d5aa7c19bf95616c57a80e1d04a7e1da35ce2ca/core/src/main/java/com/seibel/distanthorizons/core/util/threading/ThreadPoolUtil.java#L117-125)

**建议：**按“原版交接带已有真数据→视野内已缓存精细 LOD→近处精修→移动前方→远处与后台预测”分配可调预算。继续禁止不满足 0.0.3 质量门槛的预测网格占位；减少无需显示的中间网格构建，同时保留可复用高度/材质样本。父层建立可能是八叉树发现与正确细分的依赖，不能盲目让所有子层永远抢占父层；缺失模型任务也需要退避和重试保障。远处任务加等待年龄，防止长时间完全饥饿。

**验收指标：**从接收完整数据到交接带首个精细网格可见的时间、近景精细覆盖达到 50%/90% 的时间、已知真实范围内的空白持续时间；每秒新建但从未可见的网格数；重访时重复采样和重烘焙数；p95/p99 帧耗时。默认/custom RTF 的同种子同轨迹冷缓存和热缓存各跑；采样地形误差、边界截图不得退化。不能用粗层填满后宣称“完成更快”。

### 3. 客户端按真正消费能力控制下载，并让故障附属可退让

**源码证据：**LSS `LodRequestManager` 同时检查解码队列条数、字节数和消费者报告的摄入区段数；说明明确指出，非阻塞转交 Voxy 无界队列会使解码队列看似正常、实际堆内存持续上涨。其消费者停止线为 6144 区段，解码采用 3/4 容量停止线，并能撤销本次待下载集合。[请求与背压实现](https://github.com/VoX/lod-server-support/blob/178d9cd4411ba9e3423e0278d6a39bf755db171a/xplat/src/main/java/dev/vox/lss/networking/client/LodRequestManager.java#L629-L651)

**建议：**使用现有 VSS 0.3.5 能实际支持的协议表达减速/暂停，不假定新版空 want-set 替换语义可直接用于旧协议。先独立接入真实摄入积压与队列字节数，再考虑上传/网格压力；恢复采用低水位和渐进提速，避免满载边缘抖动。Xaero 等附属的停顿与真实体素消费者区分：坏地图写入器可以暂时退出节流，真实体素尚未落地则必须继续保护和重试。

**补充证据：**LSS 地图 `IngestBackpressureGovernor` 具有 pump 过期检测、无进展 wedge、回退与重启窗口，并区分主动节奏暂停与堆压力暂停；这些用于避免故障地图消费者把 LOD 下载永久锁住，不代表可以无视真实内存压力。[地图控制器](https://github.com/VoX/lod-server-support/blob/178d9cd4411ba9e3423e0278d6a39bf755db171a/xplat/src/main/java/dev/vox/lss/compat/IngestBackpressureGovernor.java#L29-L35)

**验收指标：**慢体素化/慢磁盘/地图暂停下的解码字节、摄入项目数、内存峰值；停止与恢复次数；恢复后遗漏区段数；下载字节中重复比例；退出重进真实数据保留；时间窗禁止时发送量为 0，管理员上限与每玩家公平性不退化。

### 4. 相同真实区块共享读取与编码，I/O 拥塞时主动让出容量

**源码证据：**LSS `AdaptiveReadThrottle` 使用读操作延迟 EWMA 和加法增长/乘法回退来调整在途读取数，保留最少一个请求观察恢复。源码也承认这一指标混合磁盘、解析 CPU 和线程调度延迟，且不是真正的普通区块 I/O 插队优先级。[AdaptiveReadThrottle](https://github.com/VoX/lod-server-support/blob/178d9cd4411ba9e3423e0278d6a39bf755db171a/common/src/main/java/dev/vox/lss/common/processing/AdaptiveReadThrottle.java#L5-L47)

**建议：**服务器将“世界实例 + 区块坐标 + 内容修订号 + 编码格式”的相同在途读取合为一次，多玩家共享不可变结果，取消一个订阅者不取消其他人的有效读取。热缓存同时限制条目、字节与寿命；内容发生修改、世界关闭和配置变化时按真实语义失效。原始 NBT/快照复用与编码结果复用分别量度。避免跨世界键遗漏，也不要因为共享缓存存在就绕过每玩家带宽扣费。对已有有界读取池补充保守的拥塞回退，不额外创建不受控线程池。

**验收指标：**N 玩家请求相同列的物理读取次数、解压/编码次数、缓存命中率和陈旧命中数；不同世界同坐标不命中；删除/空气更新重新读取；队列和缓存字节硬上限；普通区块加载延迟、服务端 tick p95/p99、单次最长主线程 LOD 工作。冷随机访问不能只用热点缓存成绩掩盖。

### 5. 材质与表面质量专项验证，保留原生网格细节

**源码证据：**所读 NeoVoxy `Mipper` 对水面占比、不透明覆盖、表层载体及少量固体子体素处理有专门路径；`WorldVoxilizedSectionMipper` 每层解析表层载体，避免边界信息在连续降采样中被空气捷径吞掉。这说明质量要验证具体体素/材质规则，不是仅把地形高度拟合到 90%。[Mipper](https://github.com/NHblock-Johnsnow/neo-voxy-multiversion/blob/46a0962f7dc5fefe69974d806e24852a18ba0ed5/src/main/java/me/cortex/voxy/common/world/other/Mipper.java#L120-L130)、[逐层处理](https://github.com/NHblock-Johnsnow/neo-voxy-multiversion/blob/46a0962f7dc5fefe69974d806e24852a18ba0ed5/src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java#L23-L43)

**建议：**先建立水岸、屋顶、薄墙、树冠、带水植物、斜坡和区段边界的体素与网格回归样本，定位现有规则的实际损失，再独立实现必要修复。默认不启用半分辨率交接遮罩、减少遮挡采样、粗化不透明模型等画质取舍。GPU 缓存复用必须以相机/投影、世界 epoch、模型修订、光影与深度配置共同失效，不能仅凭“玩家没动”跳过更新。

**验收指标：**真实区块转换后方块/光照保持、各层水面高度和薄结构存活率、原版交接带露天空像素与闪烁、正常与 Iris 两路径深度/透明次序、转身后的精细覆盖恢复时间。RTF 植被与结构应单独报告，不能以高度采样准确率代替整体视觉准确率。

## 为什么不直接合并 DH 或 C2ME

DH 借鉴价值是共享线程总预算、已缓存 LOD 的呈现优先和生成背压。其 `PriorityTaskPicker` 用全局 occupiedThreads 限制并行任务，在同优先级比较累计运行时间，并对暂停队列定时重新检查；逻辑任务队列本身仍是无界 `ConcurrentLinkedQueue`，因此也不能当作完整内存上限方案。[任务调度器](https://gitlab.com/jeseibel/distant-horizons-core/-/blob/8d5aa7c19bf95616c57a80e1d04a7e1da35ce2ca/core/src/main/java/com/seibel/distanthorizons/core/util/threading/PriorityTaskPicker.java#L104-L144)

C2ME 针对真实世界生成、加载与 I/O 的线程化，不是替代 LOD 渲染器；其 1.21.1 NeoForge 补丁甚至专门更改 full chunk events 的延迟策略，说明事件发生线程和次序具有平台约束。接入 LOD 时应遵守已有 API 与快照边界，不再从任意后台线程调用真实世界对象。[NeoForge 事件补丁](https://github.com/RelativityMC/C2ME-neoforge/blob/13f9747dcbf92d3244426382f7b830f9197c15b8/patches/main/0006-fix-enable-delayFullChunkEvents.patch)

当前 C2ME NeoForge 的 OpenCL 问题报告包含 1.21.1 特定 LWJGL/Rusticl 初始化失败；这是该环境的问题证据，不能推导用户硬件一定受影响，也不足以把 GPU 预测列为本版默认加速方案。[官方问题与维护者回复](https://github.com/RelativityMC/C2ME-neoforge/issues/94)

## 许可证与采用方式

- NeoVoxy 与 Voxy 上述提交的许可证均明确写有 **All rights reserved / Do not redistribute**。源码可读取不等于允许复制并发布；本调研只提供设计分析，不导入其新代码。本仓库既有二进制的来源记录也不等于已取得公开再分发许可，应单独核对现有授权。[NeoVoxy 许可证](https://github.com/NHblock-Johnsnow/neo-voxy-multiversion/blob/46a0962f7dc5fefe69974d806e24852a18ba0ed5/LICENSE.md)、[Voxy 许可证](https://github.com/MCRcortex/voxy/blob/534d58ec8b4aa412ef314b884295552c69d480a6/LICENSE.md)
- LSS 与所读 C2ME NeoForge 为 MIT；如实际移植受其覆盖的代码，应保留相应版权及许可文本，并明确改动和来源；不能把 MIT 推及本整合包其他组件。[LSS 许可证](https://github.com/VoX/lod-server-support/blob/178d9cd4411ba9e3423e0278d6a39bf755db171a/LICENSE)、[C2ME 许可证](https://github.com/RelativityMC/C2ME-neoforge/blob/13f9747dcbf92d3244426382f7b830f9197c15b8/LICENSE)
- DH 文件头声明 LGPL v3。建议参考任务调度思想、独立实现本模组的边界；不要直接混入 DH 类再把整个整合包标成 MIT。[DH 文件头](https://gitlab.com/distant-horizons-team/distant-horizons/-/blob/6763cc8a6f8e96bc59e258bf48d96707b41842b3/license_header.txt)

## 本版可接受的性能证据

固定 NeoForge **21.1.250**、相同 Java、同一 RTF 存档/自定义预设、相同视角轨迹与设置，对照 0.0.3；至少分别记录冷缓存和重进热缓存。调优开关关闭必须恢复原路径，保存数据格式和质量开关不随性能预设偷偷变化。报告真实/精细 LOD 首次可见及 90% 覆盖时间、摄入吞吐、读取复用、p95/p99 帧耗时、服务器 tick、峰值堆/原生/显存、所有队列字节上限、跨服迟到任务及本地保存完整性。

这些指标是验收要求，不是已取得的成绩。本次没有测出任何 FPS 提升百分比或“全场景无缝”结论；已有 0.0.3 的 RTF 高度采样成绩也不能代替 0.3.0 的真实区块、网格、水面和植被测试。
