# HXZ 客户端选项表

使用 `/voxyhxz menu` 或钠的 Fantasy Town Voxy 分类设置；`/voxyhxz set 选项名 值` 仍保留。以下是新配置默认值，保存到 `config/voxy-hxz-neovoxy.json`。默认 NeoVoxy 画面与性能调度同时开启；“HXZ 画面优化”仅在 HXZ 画面下生效，“性能与加载”适用于两种画面。

面板还提供 NeoVoxy 与 VSS 的全部当前客户端运行参数，包含共享、预测、RTF、植被、结构、缓存、调试和原有导入入口。版本号和废弃参数不作为用户设置。导入、管理员操作、热更新、手动世界标识都直接在钠的对应分类内，命令和模组配置按钮也打开钠。

使用钠的“应用 / Apply”保存。数值项可右键精确输入；HXZ 专属项在原版渲染模式下变灰。管理员先读取服务器实际配置，再修改并应用，服务器回读不会丢弃客户端草稿。应用后热更新；下表原先要求重进世界的资源类参数，现在自动重建远景，可能短暂停顿。旧 LOD 导入期间推迟重建。修改文件后使用“从文件热重载”或 `/voxyhxz reload`；不是自动文件监控。

| 选项 | 默认值 | 范围 | 说明 |
|---|---|---|---|
| `originalRendering` 原版 NeoVoxy 渲染 | `true` | true / false | 默认原版；关闭后启用 HXZ 可调优化。VSS 与世界隔离独立控制。 |
| `performanceScheduling` 性能调度总开关 | `true` | true / false | 渐进加载、动态视距和 HXZ 资源预算总开关，与画面模式独立。 |
| `sharedPredictionBudget` 预测跟随负载 | `true` | true / false | 前台帧耗时、堆占用或任务积压过高时，暂停非紧急预测的新任务；不取消正在执行的任务。 |
| `sessionGuards` 转服与地图任务校验 | `true` | true / false | 检查连接监听器、维度和 Xaero 地图会话，拒绝过期数据。 |
| `xaeroMapIsolation` Xaero 跨服地图隔离 | `true` | true / false | 后端身份隔离小地图、世界地图和路标目录；需重进生效，旧目录保留。 |
| `predictionCacheIsolation` 按地形预设隔离预测缓存 | `true` | true / false | 生成器、RTF 预设和注册表等变化后使用独立预测缓存；旧文件保留。 |
| `stablePrediction` 稳定地形预测 | `true` | true / false | 低模与精修共用地表和水面采样，RTF 预览保留滤波；关闭恢复 VSS 快速预览。 |
| `predictionStrictSampling` 预测网格逐点采样 | `true` | true / false | 网格每个节点都采样，避免插值漏掉山脊、河岸；关闭允许自适应插值。 |
| `predictionSurfaceFeatures` 补全冰山与恶地地形 | `true` | true / false | Java / RTF 预测执行原版地表扩展，保留冰山下方的空隙；不请求或生成真实区块。 |
| `sableCompatibility` 航空学存储区保护 | `true` | true / false | 排除 Sable 船体存储坐标；没有安装 Sable 时无额外依赖。 |
| `adaptiveDistance` 动态视距 | `true` | true / false | 依据实际帧耗时、堆内存和工作积压调节。目标帧率不是性能保证。 |
| `progressiveLoading` 渐进加载 | `true` | true / false | 进入世界后由近到远缓慢扩展；可独立关闭。 |
| `frameBudget` 加载工作预算 | `true` | true / false | 按经过的时间限制请求和根节点加载，避免高帧率导致更多后台工作。 |
| `nearFirstLoading` 优先加载近处 | `true` | true / false | 按离相机的距离处理根节点。应用后重建远景。 |
| `limitCoarseMeshes` 限制巨型粗模型 | `true` | true / false | 按模型投影面积限制粗模型；细模型未就绪时可能短暂留空。 |
| `cameraPlaneFix` 相机近处细化 | `true` | true / false | 对跨越相机平面的粗模型继续细化，避免投影异常。 |
| `safeMeshGuards` 网格有效性检查 | `true` | true / false | 阻止无效网格和子节点进入 GPU 队列。 |
| `strictRequestLimit` 严格请求上限 | `true` | true / false | 多 GPU 线程竞争时仍遵守请求预算。 |
| `frustumCulling` 视野外剔除 | `true` | true / false | 关闭后遍历阶段不剔除视野外节点；更耗性能。原版也有视锥剔除。 |
| `occlusionCulling` 遮挡剔除 | `true` | true / false | 关闭后遍历阶段不剔除被遮住的节点。 |
| `skipStartupGc` 跳过进世界强制 GC | `true` | true / false | 跳过 NeoVoxy 渲染器创建时的显式完整 GC。 |
| `limitWorkers` 限制后台线程 | `true` | true / false | 限制 Voxy 专用工作线程。此 NeoVoxy 基线没有启用 Sodium 线程共享。应用后重建远景。 |
| `limitMemory` 限制缓存与显存预算 | `true` | true / false | 限制新建世界缓存和几何缓冲。应用后重建远景。 |
| `worldIsolation` 多世界缓存隔离 | `true` | true / false | 用后端身份隔离同种子世界。服务端无身份时按稳定地址保存。相同入口同种子的独立后端需要不同服务端身份。应用后更新共享会话和缓存。 |
| `receiveServerLod` 接收服务器 LOD | `true` | true / false | 允许 VSS 握手和共享。应用后更新共享会话和缓存。 |
| `existingTerrainFix` 读取服务器已生成地形 | `true` | true / false | 关闭世界生成时仍可请求服务器已存盘区块。 |
| `minimumFps` 目标最低帧率 | `30` | 30–240 | 只调节本模组负载，无法保证整合包始终达到此帧率。 |
| `warmupSeconds` 进入世界缓冲秒数 | `5` | 0–60 | 渐进加载在这段时间保持近处范围。 |
| `growthBlocksPerSecond` 每秒扩展方块数 | `128` | 16–1024 | 达到性能余量后扩展至 NeoVoxy 设定视距。 |
| `backgroundDetail` 视野外预加载 | `1` | 0–2 | 0=停止额外准备；1=少量粗模型；2=按距离细化。始终受独立请求预算约束。 |
| `backgroundRequestsPerSecond` 视野外每秒请求 | `4` | 0–32 | 只在前台请求有余量时准备隐藏地形。 |
| `coarseScreenPercent` 粗模型最大屏幕面积百分比 | `4` | 1–25 | 越小越能限制巨大粗模型，也可能延长空白等待。 |
| `backgroundThreads` 后台线程上限 | `2` | 1–8 | 开启线程限制时使用；应用后重建远景。 |
| `sectionCacheMiB` 区段缓存预算 MiB | `64` | 16–512 | 仅限制空闲区段缓存，不是整个 Java 堆上限。应用后重建远景。 |
| `geometryMiB` 几何缓冲预算 MiB | `512` | 128–2048 | 不是显卡总占用上限。应用后重建远景。 |
| `meshRequestsPerSecond` 每秒网格请求上限 | `120` | 16–1024 | 渐进启动和负载紧张时进一步降低；性能调度与加载工作预算开启时生效。 |
| `rootUpdatesPerSecond` 每秒远景列更新上限 | `12` | 2–128 | 根节点加载速度，与网格请求分别计量。 |

性能预设仅设定可编辑的起点，不自动判断硬件，也不改变最大视距、画面模式和世界身份：

| 预设 | Voxy 工作线程上限 | 空闲区段 / 几何缓冲 MiB | 网格请求 / 根节点每秒 | 正常每秒扩展方块 | VSS 预测计算强度 |
|---|---:|---|---|---:|---|
| 低端 `low` | 1 | 32 / 256 | 60 / 6 | 64 | low |
| 均衡 `balanced` | 2 | 64 / 512 | 120 / 12 | 128 | medium |
| 高端 `quality` | 4 | 128 / 1024 | 360 / 32 | 256 | high |

三种预设均使用 5 秒初始缓冲和 30 FPS 调度目标。稳定帧率接近目标时以半速试探扩距；触发压力回退后至少冷却 3 秒。`/voxyhxz profile original` 同时关闭性能调度；面板单独切换 NeoVoxy 画面不关闭它。

Xaero 地图隔离和手动后端标识属于存储身份选项，需重新进入服务器才会更换作用域，避免同一地图会话中途换目录。

三个预测精度选项在“地形预测”页，与 NeoVoxy / HXZ 画面选择独立。应用后重建远景并分离预测缓存；真实 LOD 不移动或删除。稳定模式增加首次采样成本，仍遵循预测线程与负载预算。低端电脑可先降低预测距离和计算强度；植被数量可在同页选择“完整 100%”，植被不计入地形高度准确率。测试定义与结果见 [预测对照](PREDICTION_ACCURACY.zh-CN.md)。

## 0.0.2 地形预测新增选项

| 名称 | 默认 | 用途 |
|---|---|---|
| 粗预测使用连续山坡 | 开启 | 连接较粗采样点，避免把单个高度扩成巨大方块；应用时重建预测并使用新的预测缓存。精细方块和复杂结构仍沿用 VSS。 |
| 预测地形与水面遮挡修正 | 开启 | 无光影时协调 NeoVoxy 和 VSS 深度及透明水面绘制顺序；立即生效。 |

位于钠 → Fantasy Town Voxy → 地形预测。两项均可关闭，与 NeoVoxy/HXZ 画面及性能预设独立；预测总开关仍由用户控制。更新不会自动打开用户已关闭的预测。旧真实 LOD 不受预测缓存变化影响。
