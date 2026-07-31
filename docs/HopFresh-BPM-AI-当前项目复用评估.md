# HopFresh BPM / AI 当前项目复用评估

> 文档日期：2026-07-16  
> 文档性质：只读调研结论，供后续确定复用范围使用  
> 当前约束：本文不确定 AgentTo 的最终业务流转细节，也不代表已经决定迁移 HopFresh 代码

## 1. 结论先行

HopFresh 有参考价值，但不适合把 `yudao-module-bpm` 和 `yudao-module-ai` 原样合并到 AgentTo。

更合适的处理方式是：

1. **BPM 作为主要复用对象。** HopFresh 已经把 Flowable 的模型、表单、部署、实例、任务操作、候选人、事件通知和业务回写串成了完整链路。AgentTo 当前只有 `WorkflowGateway` 和内存实现，还没有真正的流程引擎，因此 BPM 是最值得继续拆解和复用的部分。
2. **保留 AgentTo 的工作流边界。** 不建议让业务代码直接依赖 Flowable。继续保留 `WorkflowGateway`，在它后面增加 HopFresh/Flowable 适配器。这样即使公司最终提供的是另一套已接企微的工作流代码，也不需要推翻文件、审查和 Agent 业务层。
3. **AI 不按整模块迁移。** HopFresh 的模型密钥管理、模型工厂、会话、流式消息、角色、工具和 MCP 可以参考或选择性迁移；但需要从 Spring AI 1.1.0 适配到 AgentTo 的 Spring AI 2.0.0，不能直接复制后即用。
4. **现有独立 RAG 不替换。** AgentTo 的 RAG 已经形成独立服务，并具备 DOCX 结构化切分、Elasticsearch 关键词与向量双路召回、RRF、TEI Embedding、TEI Rerank、内容去重、执行降级和全过程 Trace。HopFresh 当前真正执行的主链是 `SimpleVectorStore` 加可选 Rerank，不应反向替换 AgentTo 现有实现。
5. **TinyFlow 暂不进入主链。** 它和业务审批工作流不是同一类东西，而且当前源码中 TinyFlow 的模型提供方只处理 TongYi 和 Ollama。AgentTo 的自审和秘书组 Agent 先由 Spring AI 编排更合适。
6. **工程、选址、项目审批等公司定制代码不迁移。** 这些代码虽然能证明 HopFresh 的流程回写模式可用，但它们绑定了 HopFresh 自己的业务表、外部库和组织规则，只适合作为样例。
7. **目前只能确认静态复用可行，运行兼容性仍未确认。** 本次没有启动 HopFresh 全套服务，也没有连接它的 Nacos、数据库、Redis、文件服务和第三方模型。AgentTo 当前固定的 Spring Boot 4.0.6 与 Flowable 7.2.0 的实际兼容性、现有数据库完整结构、公司代码的授权范围，都需要在迁移前单独确认。

简化成一句话：**BPM 复用框架和主链，AI 复用管理与编排思路，RAG 保留 AgentTo 现有实现，所有 HopFresh 平台级依赖都做隔离。**

## 2. 调研范围和证据口径

本次结论同时核对了四层资料：

1. HopFresh 已整理的 BPM / AI 全景、GitNexus Wiki 和审计证据；
2. `hopfresh-server` 的 Controller、Service、Mapper、DO、Flowable 扩展和配置源码；
3. `hopfresh-web` 的页面、API 封装和实际调用位置；
4. GitNexus 现有索引中的符号、调用关系和模块关系。

本次核对的源码快照为：

- `hopfresh-server`：`137c2970511d6336b99a437446664d2c09b8ba13`；
- `hopfresh-web`：`1c0cea8a3ae47613caadb42cb6a62e8b3db77d3b`。

GitNexus 现有索引与以上提交一致。由于本次只读分析没有修改 HopFresh 仓库，也没有处理仓库可能存在的未提交文件，本文只对实际读到的当前文件内容负责。

GitNexus 当前可以确认 Java 方法之间的调用关系，例如：

- `BpmModelController -> BpmModelServiceImpl.deployModel`；
- `BpmProcessInstanceApi -> BpmProcessInstanceServiceImpl.createProcessInstance`；
- `BpmTaskController -> BpmTaskServiceImpl.approveTask / rejectTask / returnTask`；
- `AiChatMessageController -> AiChatMessageServiceImpl.sendChatMessageStream`；
- `AiKnowledgeSegmentController -> AiKnowledgeSegmentServiceImpl.searchKnowledgeSegment`。

但本次索引的 embeddings 为 0，部分 process 结果为空，Vue SFC 到 API 方法的 incoming 引用也不完整。因此，GitNexus 主要用于定位和交叉核对，页面调用和最终行为仍以源码为准。

## 3. BPM 业务能力概览

### 3.1 模块结构

HopFresh BPM 位于：

- `yudao-module-bpm/yudao-module-bpm-api`：对外接口、事件和 DTO；
- `yudao-module-bpm/yudao-module-bpm-server`：Controller、Service、Mapper、Flowable 封装、监听器和业务样例。

它不是只对 Flowable API 做了一层薄包装，而是在 Flowable 上补了较完整的平台能力：

| 领域 | 已有能力 |
|---|---|
| 流程分类与模型 | 分类、模型列表、BPMN 模型、简易设计器模型、复制、导入、部署、停用 |
| 表单 | 表单定义、表单设计、字段权限、流程表单绑定 |
| 流程定义 | 部署、版本、状态、定义信息扩展 |
| 流程实例 | 发起、取消、详情、抄送、历史、下一审批节点预测 |
| 审批任务 | 待办、已办、通过、驳回、退回、转办、委派、加签、减签、撤回、抄送 |
| 候选人 | 用户、部门、岗位、角色、用户组、发起人相关规则等 16 类候选策略 |
| 监听与事件 | 任务创建、指派、完成、活动取消、定时器、流程创建、结束、取消 |
| 业务回写 | 业务模块通过流程定义 Key 监听状态事件，更新自己的业务表 |
| 通知 | 站内信/流程消息；部分短信调用存在，但通过/驳回短信发送代码已被注释 |
| 统计与扩展 | 流程统计、公司工程/项目审批等定制模块 |

### 3.2 Flowable 与项目代码的边界

Flowable 负责：

- BPMN 仓库、模型和部署；
- 流程实例、执行流和变量；
- 任务、历史和定时任务；
- 原生运行表和历史表。

HopFresh 自己负责：

- 模型附加信息和两种设计器数据；
- 表单、字段权限和候选人配置；
- 完整任务操作入口；
- 自定义 UserTask 行为和候选人计算；
- Spring 事件发布、消息通知和业务回写；
- 租户、权限、组织、文件和企业微信等平台接入。

关键边界源码：

- `BpmFlowableConfiguration`：注册 Flowable 监听器、表达式函数、自定义 ActivityBehaviorFactory 和执行器；
- `BpmActivityBehaviorFactory`：替换普通 UserTask 和多实例 UserTask 行为；
- `BpmUserTaskActivityBehavior`：创建任务时计算候选人；
- `BpmTaskServiceImpl`：封装审批动作；
- `BpmProcessInstanceEventPublisher`：把 Flowable 生命周期转换为 Spring 业务事件。

这套边界设计可以参考，但 `BpmTaskServiceImpl` 的加签、退回、撤回等实现使用了 Flowable 内部类和运行时迁移 API，和 Flowable 版本存在较强绑定，不能简单升级版本。

## 4. BPM 核心入口和关键调用链

### 4.1 流程模型部署

调用链：

`模型管理页面`  
→ `POST /bpm/model/deploy`  
→ `BpmModelController.deployModel`  
→ `BpmModelServiceImpl.deployModel`  
→ 校验模型、BPMN、表单和候选人配置  
→ `BpmProcessDefinitionServiceImpl.createProcessDefinition`  
→ Flowable `RepositoryService.createDeployment`  
→ 写入 Flowable 仓库表和 `bpm_process_definition_info`  
→ 停用旧版本并回写 deploymentId。

源码证据：

- `yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmModelController.java`
- `.../service/definition/BpmModelServiceImpl.java:215`
- `.../service/definition/BpmProcessDefinitionServiceImpl.java:135`
- `.../dal/mysql/definition/BpmProcessDefinitionInfoMapper.java`

### 4.2 业务模块发起流程

对外入口为：

- `BpmProcessInstanceApi.createProcessInstance(Long userId, BpmProcessInstanceCreateReqDTO reqDTO)`；
- 查询当前用户相关流程实例；
- 查询流程发起人。

典型调用链：

`业务 Service 保存业务记录`  
→ `BpmProcessInstanceApi.createProcessInstance`  
→ `BpmProcessInstanceServiceImpl.createProcessInstance`  
→ 校验启用中的流程定义、表单变量和指定审批人  
→ 设置 authenticated user  
→ `createProcessInstance0`  
→ Flowable `RuntimeService.createProcessInstanceBuilder().businessKey(...).start()`  
→ 将 processInstanceId 回写业务表。

请假样例验证了这条链路：

- `BpmOALeaveServiceImpl.createLeave` 先写 `bpm_oa_leave`，再调用 `BpmProcessInstanceApi`，最后保存流程实例 ID；
- `BpmOALeaveStatusListener` 监听对应 processDefinitionKey；
- `BpmOALeaveServiceImpl.updateLeaveStatus` 把最终状态写回业务表。

这正是 AgentTo 后续应该采用的模式：**流程实例只管理流转，AgentTo 自己的任务、文件版本和审查轮次仍保存在业务表中，两者通过 businessKey / mapping 表关联。**

源码证据：

- `yudao-module-bpm-api/src/main/java/cn/iocoder/yudao/module/bpm/api/task/BpmProcessInstanceApi.java`
- `.../service/task/BpmProcessInstanceServiceImpl.java:760`
- `.../service/task/BpmProcessInstanceServiceImpl.java:790`
- `.../service/oa/BpmOALeaveServiceImpl.java:47`
- `.../service/oa/listener/BpmOALeaveStatusListener.java`

### 4.3 创建和办理审批任务

任务由 Flowable 推进流程时创建。HopFresh 通过自定义 UserTask behavior 计算候选人，并由任务监听器补齐状态、通知和超时处理。

主要审批动作都集中在 `BpmTaskServiceImpl`：

| 动作 | Controller 接口 | Service 方法 | 主要机制 |
|---|---|---|---|
| 通过 | `PUT /bpm/task/approve` | `approveTask` | 校验任务和实例，设置变量，调用 `taskService.complete` |
| 驳回 | `PUT /bpm/task/reject` | `rejectTask` | 结束或迁移当前流程，并记录意见 |
| 退回 | `PUT /bpm/task/return` | `returnTask` | `ChangeActivityStateBuilder` 迁移到指定活动 |
| 转办 | `PUT /bpm/task/transfer` | `transferTask` | 变更 assignee |
| 委派 | `PUT /bpm/task/delegate` | `delegateTask` | Flowable delegate/resolve 机制 |
| 加签 | `PUT /bpm/task/create-sign` | `createSignTask` | 动态创建父子任务，使用 Flowable 内部 TaskEntity |
| 减签 | `DELETE /bpm/task/delete-sign` | `deleteSignTask` | 删除或完成加签任务 |
| 撤回 | `PUT /bpm/task/withdraw` | `withdrawTask` | 校验已办任务后迁移活动状态 |
| 抄送 | `PUT /bpm/task/copy` | `createCopyTask` | 写 `bpm_process_instance_copy` |

源码证据：

- `.../controller/admin/task/BpmTaskController.java:175`
- `.../service/task/BpmTaskServiceImpl.java:699`
- `.../service/task/BpmTaskServiceImpl.java:984`
- `.../service/task/BpmTaskServiceImpl.java:1054`
- `.../service/task/BpmTaskServiceImpl.java:1268`
- `.../service/task/BpmTaskServiceImpl.java:1299`
- `.../service/task/BpmTaskServiceImpl.java:1369`
- `.../service/task/BpmTaskServiceImpl.java:1487`
- `.../service/task/BpmTaskServiceImpl.java:1526`

### 4.4 流程结束和业务结果回写

调用链：

`Flowable 流程完成/取消`  
→ `BpmProcessInstanceEventListener`  
→ `BpmProcessInstanceServiceImpl` 更新和收尾  
→ `BpmProcessInstanceEventPublisher` 发布 `BpmProcessInstanceStatusEvent`  
→ 业务模块的 `BpmProcessInstanceStatusEventListener` 按流程定义 Key 过滤  
→ 业务 Service 更新自己的状态字段。

`BpmProcessInstanceStatusEventListener` 只负责事件过滤和抽象回调，不知道具体业务表。这个设计适合 AgentTo：后续由 AgentTo 的监听器更新 `at_task`、`at_review_round` 和审计记录，不让 BPM 模块反向依赖文件业务。

源码证据：

- `.../framework/flowable/core/listener/BpmProcessInstanceEventListener.java`
- `.../framework/flowable/core/event/BpmProcessInstanceEventPublisher.java`
- `yudao-module-bpm-api/src/main/java/cn/iocoder/yudao/module/bpm/api/event/BpmProcessInstanceStatusEventListener.java`
- `.../service/task/BpmProcessInstanceServiceImpl.java:970`

## 5. AI 业务能力概览

### 5.1 模块结构

HopFresh AI 位于：

- `yudao-module-ai/yudao-module-ai-api`：平台、模型类型等枚举和对外定义；
- `yudao-module-ai/yudao-module-ai-server`：模型工厂、聊天、知识库、图像、音乐、写作、思维导图、TinyFlow 等功能。

### 5.2 实际支持范围

`AiPlatformEnum` 定义了 20 个平台，但“枚举中出现”不等于所有能力都支持。

| 能力 | 源码确认范围 | 说明 |
|---|---:|---|
| 平台枚举 | 20 | 包含 DeepSeek、通义、智谱、豆包、硅基流动、OpenAI、Ollama 等 |
| ChatModel 创建分支 | 17 | 由 `AiModelFactoryImpl.getOrCreateChatModel` 的实际分支确认 |
| ImageModel 创建分支 | 6 | 图像能力只覆盖部分平台 |
| EmbeddingModel 创建分支 | 7 | 不能把 20 个平台都视为可做向量化 |
| TinyFlow 模型注入 | 2 | 当前 `AiWorkflowServiceImpl` 只处理 TongYi 和 Ollama |

因此，AgentTo 不需要迁移“20 平台”这个数量。第一阶段只保留统一模型配置接口，并验证 DeepSeek、通义和智谱的兼容调用即可。

### 5.3 聊天、Prompt、工具和 MCP

聊天主链包括：

`聊天页面`  
→ `POST /ai/chat/message/send-stream`（SSE）  
→ `AiChatMessageController.sendChatMessageStream`  
→ `AiChatMessageServiceImpl.sendChatMessageStream`  
→ 校验会话、角色和模型  
→ 读取历史上下文  
→ 可选知识库召回、联网搜索、附件读取  
→ 组装系统消息、历史消息和当前消息  
→ 注册 ToolCallback / MCP 客户端  
→ 调用 StreamingChatModel  
→ 持久化完成、失败或取消状态。

这条链路对 AgentTo 最有价值的不是通用聊天页面，而是以下实现经验：

- 模型和密钥从数据库选择，不把厂商写死在业务 Service；
- 历史消息成对读取，避免上下文断裂；
- 附件、检索结果、流程意见和角色要求在调用模型前统一组装；
- 流式输出同时保存完成、失败和取消状态；
- 工具和 MCP 作为独立回调接入。

AgentTo 需要将这里的“普通聊天会话”替换成“任务 ID + 审查轮次 ID + Agent 运行 ID”上下文，不能照搬会话表就直接使用。

源码证据：

- `.../controller/admin/chat/AiChatMessageController.java:59`
- `.../service/chat/AiChatMessageServiceImpl.java:195`
- `.../service/chat/AiChatMessageServiceImpl.java:299`
- `.../service/chat/AiChatMessageServiceImpl.java:384`
- `.../service/chat/AiChatMessageServiceImpl.java:431`
- `.../service/chat/AiChatMessageServiceImpl.java:461`

### 5.4 HopFresh 知识库的实际主链

HopFresh 的知识库链路为：

`上传/选择文件`  
→ `AiKnowledgeDocumentServiceImpl` 读取文件  
→ Tika 提取内容  
→ `AiKnowledgeSegmentServiceImpl` 切片  
→ 保存文档和分段  
→ `VectorStore.add` 写向量  
→ `VectorStore.similaritySearch` 召回  
→ 可选 Rerank  
→ 回查数据库补齐分段内容。

需要注意四个源码事实：

1. 当前 `AiKnowledgeDocumentServiceImpl.readUrl` 统一使用 `TikaDocumentReader`，没有按 txt/md 走另一条已验证主链；
2. `AiModelServiceImpl.getOrCreateVectorStore` 当前实际返回 `SimpleVectorStore`；Qdrant、Redis、Milvus 的工厂代码存在，但主调用被注释；
3. `SEMANTIC` 切分实际由 `SemanticTextSplitter` 依据段落、句子和字符/单词估算进行切分，没有调用 Embedding 做语义断点判断；
4. 检索是向量召回加可选 Rerank，没有 AgentTo 现有的 ES BM25 + 向量双路、RRF、内容去重和全过程可观测性。

所以 HopFresh 知识库可以参考管理页面、模型配置和切分策略命名，但不应取代 AgentTo 的独立 RAG 主链。

源码证据：

- `.../service/knowledge/AiKnowledgeDocumentServiceImpl.java:58`
- `.../service/knowledge/AiKnowledgeDocumentServiceImpl.java:174`
- `.../service/knowledge/AiKnowledgeSegmentServiceImpl.java:95`
- `.../service/knowledge/AiKnowledgeSegmentServiceImpl.java:203`
- `.../service/knowledge/AiKnowledgeSegmentServiceImpl.java:228`
- `.../service/knowledge/splitter/SemanticTextSplitter.java`
- `.../service/knowledge/splitter/MarkdownQaSplitter.java`
- `.../service/model/AiModelServiceImpl.java:128`

### 5.5 TinyFlow

TinyFlow 页面可以保存和编辑图结构，后端 `AiWorkflowController` 提供 CRUD、分页和测试接口，`AiWorkflowServiceImpl.testWorkflow` 使用 TinyFlow 执行图。

它适合“模型节点、条件节点、工具节点”等 AI 编排，不适合代替 Flowable 的正式审批。当前 AgentTo 只有自审和秘书组终审阶段接 Agent，先由 Java/Spring AI 服务显式编排更容易控制权限、审计和版本。TinyFlow 可以保留为以后观察项，当前不迁移。

源码证据：

- `.../controller/admin/workflow/AiWorkflowController.java`
- `.../service/workflow/AiWorkflowServiceImpl.java:42`
- `.../service/workflow/AiWorkflowServiceImpl.java:123`

## 6. 页面 → API → Controller → Service → Mapper / 表映射

### 6.1 BPM 核心映射

| 页面 | 前端 API | Controller | Service / 引擎 | Mapper / 表 |
|---|---|---|---|---|
| `src/views/bpm/model/index.vue` | `src/api/bpm/model/index.ts`：list/create/update/deploy | `BpmModelController` | `BpmModelServiceImpl`、`BpmProcessDefinitionServiceImpl`、Flowable RepositoryService | Flowable 模型/部署/定义表；`BpmProcessDefinitionInfoMapper` → `bpm_process_definition_info` |
| `src/views/bpm/model/form/editor/index.vue` | model update-bpmn/update | `BpmModelController` | `BpmModelServiceImpl`、BPMN/SIMPLE 转换工具 | Flowable model 字节数据；定义扩展表 |
| `src/views/bpm/form/index.vue`、`form/editor/index.vue` | `src/api/bpm/form/index.ts` | `BpmFormController` | `BpmFormServiceImpl` | `BpmFormMapper` → `bpm_form` |
| `src/views/bpm/model/definition/index.vue` | `src/api/bpm/definition/index.ts` | `BpmProcessDefinitionController` | `BpmProcessDefinitionServiceImpl` | Flowable process definition + `bpm_process_definition_info` |
| `src/views/bpm/processInstance/create/index.vue`、`ProcessDefinitionDetail.vue` | `POST /bpm/process-instance/create` | `BpmProcessInstanceController` | `BpmProcessInstanceServiceImpl.createProcessInstance`、Flowable RuntimeService | Flowable runtime 表；businessKey 关联 AgentTo 业务表 |
| `src/views/bpm/task/todo/index.vue` | `GET /bpm/task/todo-page` | `BpmTaskController` | `BpmTaskServiceImpl.getTaskTodoPage`、TaskService/HistoryService | Flowable 运行任务和历史任务表 |
| `src/views/bpm/task/done/index.vue` | `GET /bpm/task/done-page`、`PUT /withdraw` | `BpmTaskController` | `BpmTaskServiceImpl` | Flowable 历史/运行表 |
| `src/views/bpm/processInstance/detail/index.vue`、`ProcessInstanceOperationButton.vue` | approve/reject/return/delegate/transfer/create-sign/delete-sign/copy | `BpmTaskController` | `BpmTaskServiceImpl` | Flowable 任务/执行/历史表；抄送写 `bpm_process_instance_copy` |
| `src/views/bpm/processInstance/index.vue`、`manager/index.vue` | my-page/manager-page/get | `BpmProcessInstanceController` | `BpmProcessInstanceServiceImpl` | Flowable runtime/history 表 |
| `src/views/bpm/task/copy/index.vue` | process-instance copy/page | `BpmProcessInstanceController` | 抄送查询 Service | `BpmProcessInstanceCopyMapper` → `bpm_process_instance_copy` |

前端源码已确认 `ProcessInstanceOperationButton.vue` 实际调用了通过、驳回、抄送、转办、委派、加签、退回、取消实例和减签接口，不是仅存在按钮名称。

### 6.2 AI 核心映射

| 页面 | 前端 API | Controller | Service | Mapper / 表 |
|---|---|---|---|---|
| `src/views/ai/model/apiKey/index.vue` | `src/api/ai/model/apiKey/index.ts` | `AiApiKeyController` | `AiApiKeyServiceImpl` | `AiApiKeyMapper` → `ai_api_key` |
| `src/views/ai/model/model/index.vue` | `src/api/ai/model/model/index.ts` | `AiModelController` | `AiModelServiceImpl`、`AiModelFactoryImpl` | `AiModelMapper` → `ai_model` |
| `src/views/ai/model/chatRole/index.vue` | chatRole API | `AiChatRoleController` | `AiChatRoleServiceImpl` | `ai_chat_role` |
| `src/views/ai/model/tool/index.vue` | tool API | `AiToolController` | `AiToolServiceImpl` | `ai_tool` |
| `src/views/ai/chat/index/index.vue` | `ChatMessageApi.sendChatMessageStream` | `AiChatMessageController` | `AiChatMessageServiceImpl` | `ai_chat_conversation`、`ai_chat_message` |
| `src/views/ai/chat/manager/index.vue` | chat message/page/delete-by-admin | `AiChatMessageController` | `AiChatMessageServiceImpl` | `AiChatMessageMapper` → `ai_chat_message` |
| `src/views/ai/knowledge/knowledge/index.vue` | knowledge API | `AiKnowledgeController` | `AiKnowledgeServiceImpl` | `ai_knowledge` |
| `src/views/ai/knowledge/document/index.vue`、`document/form/*` | document create-list/page/status/delete | `AiKnowledgeDocumentController` | `AiKnowledgeDocumentServiceImpl` | `AiKnowledgeDocumentMapper` → `ai_knowledge_document` |
| `src/views/ai/knowledge/segment/index.vue` | segment page/create/update/status | `AiKnowledgeSegmentController` | `AiKnowledgeSegmentServiceImpl` | `AiKnowledgeSegmentMapper` → `ai_knowledge_segment`；向量写入 VectorStore |
| `src/views/ai/knowledge/knowledge/retrieval/index.vue` | `GET /ai/knowledge/segment/search` | `AiKnowledgeSegmentController` | `searchKnowledgeSegment` | VectorStore + `ai_knowledge_segment` 回查 |
| `src/views/ai/workflow/index.vue`、`workflow/form/*` | workflow CRUD/test | `AiWorkflowController` | `AiWorkflowServiceImpl`、TinyFlow | `AiWorkflowMapper` → `ai_workflow` |
| 图像、音乐、写作、思维导图页面 | 各自 AI API | 对应 Controller | 对应 Service | `ai_image`、`ai_music`、`ai_write`、`ai_mind_map` |

### 6.3 已发现的前后端不一致

这些不一致说明 HopFresh 不能以“页面存在”为依据整体搬运：

- 前端 `KnowledgeSegmentApi.deleteKnowledgeSegment` 调用 `DELETE /ai/knowledge/segment/delete`，当前 `AiKnowledgeSegmentController` 没有对应 DeleteMapping；
- 前端存在 `/bpm/process-instance/get-form-fields-permission`，后端源码没有找到对应映射；
- 前端存在 `/bpm/task/my-todo`，后端源码没有找到对应映射；
- 音乐创作页面存在本地模拟逻辑，不能视为后端生成链路已经贯通；
- AI 各平台能力分支并不一致，不能按一个模型跑通后推定其他模型也可用。

以上缺口若进入迁移范围，需要逐项补接口契约测试。

## 7. AgentTo 与 HopFresh 技术栈差异

| 方面 | HopFresh | AgentTo 当前 | 影响 |
|---|---|---|---|
| Java | 17 | 21 | 搬运后需重新编译和测试 |
| Spring Boot | 根 POM 声明 3.5.4，`yudao-dependencies` 声明 3.5.8；有效版本未确认 | 4.1.0 | 自动配置、依赖兼容性需要验证 |
| Spring AI | 1.1.0；Alibaba 1.1.0.0-RC1 | 2.0.0 | Chat、Tool、MCP、VectorStore API 需要适配 |
| 后端形态 | 多模块、偏微服务，Nacos/Gateway/Feign | 单体后端 + 独立 RAG 服务 | 不应迁入整套微服务基础设施 |
| ORM/迁移 | MyBatis-Plus；完整生产 DDL 未在仓库找到 | JPA + Flyway | DO/Mapper 不能直接成为 AgentTo 持久化层 |
| 工作流 | Flowable 7.2.0，功能较完整 | `WorkflowGateway` + `InMemoryWorkflowGateway` | 适合新增 Flowable/HopFresh Adapter |
| 用户组织 | System 模块、Long userId、部门/岗位/角色/租户 | 平台自有账号和组织，后续接企微 | 候选人和权限必须改接口，不可照搬 |
| 权限 | `@PreAuthorize("@ss.hasPermission(...)")`、租户、数据权限 | 尚在建立 | Controller 注解和上下文体系需替换 |
| 文件 | Infra `FileApi` | 主系统计划 MinIO；RAG 已使用 MinIO | 改成统一文件网关/对象存储接口 |
| RAG | Tika + SimpleVectorStore + 可选 Rerank | 独立服务；ES BM25+向量、RRF、TEI、去重、Trace | 保留 AgentTo 现有链路 |
| 前端 | Vue 3.5.12、TS 5.3.3、Vite 5.1.4、Element Plus 2.11.1、Pinia 2、Router 4、bpmn-js 17 | RAG 前端 Vue 3.5.32、TS 6、Vite 8、Element Plus 2.14、Pinia 3、Router 5 | 页面能参考，但需要按当前工程升级和适配 |

HopFresh 根 POM 和依赖 BOM 对 Spring Boot 的版本声明不一致，本次没有生成 effective POM，因此有效版本标记为**未确认**。

## 8. 可以直接复用的内容

这里的“直接复用”指不依赖 HopFresh System/Infra/租户/业务表的代码或设计资产，允许进行包名、样式和接口地址这类轻量调整，并不代表可以整模块复制。

### 8.1 可以优先复用

1. **BPM 页面信息架构和交互流程**
   - 模型列表、设计、部署、流程定义、发起、待办、已办、实例详情和审批操作区的页面组织已经完整。
   - 可以沿用页面结构和用户操作路径，再替换权限指令、字典、API 类型和视觉样式。

2. **bpmn-js 设计器接入方式**
   - HopFresh 已使用 `bpmn-js 17.9.2`，模型编辑、XML 保存和部署链路可以作为当前前端接入参考。

3. **流程状态事件的解耦模式**
   - `BpmProcessInstanceStatusEvent` + 抽象监听器 + 业务 Service 回写的模式可以直接采用。
   - AgentTo 只需定义自己的事件 DTO 和监听器，不必复制 HopFresh 的 OA 业务代码。

4. **跨模块发起流程的 API 形态**
   - `BpmProcessInstanceApi` 的输入包含 userId、processDefinitionKey、businessKey、variables 和指定审批人，接口职责清楚。
   - AgentTo 可以把这一形态落在现有 `WorkflowGateway` 上。

5. **流式消息前端处理方式**
   - `fetchEventSource`、AbortController、完成/错误/关闭回调可用于 Agent 审查过程的流式展示。

### 8.2 直接复用前仍要确认

- HopFresh 代码的公司内部归属和允许复制范围：**未确认**，缺少代码所有人或公司授权结论；
- `bpmn-js`、FormCreate、TinyFlow 等依赖在公司交付场景中的许可证要求：**未确认**，缺少正式许可证审查；
- UI 是否要保留 HopFresh 原样风格：本次未做产品设计确认。

## 9. 需要适配后复用的内容

### 9.1 BPM 后端主链

建议适配复用：

- 模型、定义和部署 Service；
- Flowable 配置、自定义任务行为和候选人计算；
- 流程实例发起、查询和取消；
- 通过、驳回、退回、转办、委派、加签、减签、撤回和抄送；
- 任务/实例监听器；
- 流程结束事件和业务回写机制；
- 模型、表单、定义、任务和实例前端页面。

主要适配点：

1. 把 HopFresh 的 System 用户/部门/岗位/角色查询替换为 AgentTo 组织服务接口；
2. 把 Long userId 假设改成 AgentTo 的用户标识规范；
3. 去掉租户、数据权限和 HopFresh 菜单权限的硬耦合，再按 AgentTo 权限重新接入；
4. MyBatis-Plus 持久化改为当前项目选定的 JPA/Flyway，或在 BPM 子模块内明确保留 MyBatis-Plus；
5. 用 `WorkflowGateway` 隔离 Flowable Service；
6. 把通知抽象为 `NotificationGateway`，后续接公司现成企微代码；
7. 只迁移通用 BPM 表，不迁移 HopFresh 公司业务表；
8. 对 Flowable 内部 API 的使用增加回归测试，固定引擎版本。

### 9.2 AI 模型与 Agent 主链

建议适配复用：

- API Key / 模型配置的管理模型和页面；
- 按平台和模型类型创建 Chat/Embedding/Image 模型的工厂思想；
- 会话历史拼装、附件处理、知识召回、Tool/MCP 回调和流式状态保存；
- 角色与 Prompt 配置的部分页面和数据结构。

主要适配点：

1. Spring AI 1.1.0 改为 2.0.0 API；
2. 会话上下文改成 AgentTo 的任务、文件版本、审查轮次和 Agent 运行；
3. 知识召回改为调用独立 RAG HTTP API，不在主后端内创建 VectorStore；
4. 模型密钥需要加密保存，Controller 返回值不得回显明文；
5. Prompt 模板要加入角色、节点、文件版本、历史咨询意见、老板驳回意见和引用 chunks；
6. 只实现 AgentTo 实际需要的 ChatModel 提供方，不迁移无关的图像/音乐模型分支。

## 10. 不建议直接搬运的内容

1. **整个 `yudao-module-ai`**：范围远超 AgentTo，且绑定 System、Infra、Nacos、租户和 Spring AI 1.1.0。
2. **HopFresh 知识库主链**：当前使用 `SimpleVectorStore`，能力低于 AgentTo 已完成的独立 RAG。
3. **TinyFlow 作为正式审批引擎**：缺少正式审批需要的任务、人员、审计、退回和历史能力。
4. **图像、音乐、写作、思维导图**：与高管文件协同主目标无关，会扩大维护范围。
5. **HopFresh 公司定制 BPM 业务**：`cpm_*`、`hop_project_approval*`、`front_engineer_site_info` 和 `YOSDS.YOS_DATATO300` 都带有明确业务耦合。
6. **OA 请假表本身**：`bpm_oa_leave` 只是验证业务发起和状态回写的样例，不是 AgentTo 数据模型。
7. **整套 Gateway/Nacos/System/Infra 框架**：AgentTo 当前采用单体主后端和独立 RAG，没有必要为了 BPM 引入整套 HopFresh 平台。
8. **未对齐的前端接口**：已发现缺失映射的页面/API 不能直接搬运。

## 11. 外部依赖和启动条件

### 11.1 BPM

从 POM、配置和源码可确认依赖：

- MySQL 和 Flowable 7.2.0 原生表；
- Redis，部分流程实例 ID、锁或平台能力会使用；
- Nacos Discovery 和 Config；
- Gateway 路由；
- System API：用户、部门、岗位、角色、用户组等；
- Security、Tenant、Data Permission；
- 消息通知和企业微信相关 API；
- 文件/表单相关公共组件；
- 前端动态路由、权限指令、字典和公共组件。

模块配置显示：

- `bpm-server` 默认端口为 `48083`；
- 配置从本地 profile 及 Nacos 的 `bpm-server.yaml`、`application.yaml` 导入；
- 开启了 `allow-circular-references`。

Nacos 中实际的数据源、Redis、Flowable、租户和消息配置内容：**未确认**，因为它们不在当前源码仓库的完整静态配置中。

### 11.2 AI

从 POM、配置和源码可确认依赖：

- MySQL；
- System `AdminUserApi`；
- Infra `FileApi`；
- Security 和 Tenant；
- Nacos/Gateway；
- Spring AI 各厂商 starter；
- Tika；
- MCP；
- 外部模型 API Key；
- Bocha 联网搜索；
- XXL-Job 或定时任务能力；
- 可选 Qdrant、Redis、Milvus starter，但当前主链未启用这些 VectorStore。

模块配置显示：

- `ai-server` 默认端口为 `48090`；
- 配置从本地 profile 和 Nacos 导入。

外部模型账号是否仍有效、Bocha 配置、文件服务地址、Nacos 配置和所有模型的真实可调用范围：**未确认**，本次没有启动服务做运行验证。

## 12. 数据库表和迁移范围

### 12.1 HopFresh BPM 表

源码中的 `@TableName` 共确认 22 张 BPM 模块相关表。

通用 BPM 核心表：

- `bpm_category`
- `bpm_form`
- `bpm_process_definition_info`
- `bpm_process_expression`
- `bpm_process_listener`
- `bpm_user_group`
- `bpm_process_instance_copy`
- `bpm_process_form_template`
- `bpm_process_task_form_field_config`

OA 样例表：

- `bpm_oa_leave`

HopFresh 公司业务表：

- `cpm_construct_project`
- `cpm_construct_project_report`
- `front_engineer_site_info`
- `YOSDS.YOS_DATATO300`
- `hop_project_approval`
- `hop_project_approval_finance_roi`
- `hop_project_approval_investment`
- `hop_project_approval_kpi`
- `hop_project_approval_member_cost`
- `hop_project_approval_milestone`
- `hop_project_approval_milestone_log`
- `hop_project_approval_user`

审计资料中开发数据库还发现 45 张 Flowable 原生表，覆盖 `ACT_GE_*`、`ACT_RE_*`、`ACT_RU_*`、`ACT_HI_*` 等系列。仓库现有 BPM 测试 SQL 只覆盖少量 H2 风格表，主 MySQL SQL 文件也没有完整的 BPM/AI/Flowable `CREATE TABLE`。

因此，迁移时不能从零散测试 SQL 拼生产库。需要选择以下一种可靠来源：

1. Flowable 7.2.0 官方 MySQL schema；
2. HopFresh 当前受控数据库的结构导出，并和 DO/引擎版本核对；
3. 在全新数据库中由确认后的 Flowable schema 管理策略初始化。

具体采用哪一种：**未确认**，缺少公司现有工作流数据库和部署方式。

### 12.2 HopFresh AI 表

源码确认 14 张：

- `ai_api_key`
- `ai_model`
- `ai_chat_role`
- `ai_tool`
- `ai_chat_conversation`
- `ai_chat_message`
- `ai_knowledge`
- `ai_knowledge_document`
- `ai_knowledge_segment`
- `ai_image`
- `ai_music`
- `ai_write`
- `ai_mind_map`
- `ai_workflow`

建议第一阶段只评估前六张与模型管理、角色、工具和 Agent 运行相关的结构。`ai_knowledge*` 不迁移，继续使用 AgentTo 独立 RAG 的 `rag_*` 表；图像、音乐、写作、思维导图、TinyFlow 表暂不迁移。

### 12.3 AgentTo 当前表

主后端已有 Flyway 表：

- `at_task`
- `at_review_round`
- `at_task_participant`
- `at_workflow_mapping`
- `at_audit_event`

独立 RAG 已有：

- `rag_admin_user`
- `rag_admin_session`
- `rag_document`
- `rag_document_version`
- `rag_ingestion_job`
- `rag_ingestion_stage`
- `rag_chunk`
- `rag_query_trace`
- `rag_query_candidate`

推荐保留所有 `at_*` 和 `rag_*` 表。BPM 新增的是流程引擎表和通用定义表，不应把 AgentTo 的文件业务表替换掉。`at_workflow_mapping` 可继续承担 taskId / reviewRoundId 与 processInstanceId / processDefinitionKey 的关联。

## 13. 权限、租户、用户和组织体系耦合

HopFresh 的耦合点主要在：

1. Controller 的 `@PreAuthorize("@ss.hasPermission(...)")`；
2. `BaseDO`、租户拦截器和 tenantId；
3. System API 的 AdminUser、Dept、Post、Role、UserGroup；
4. 候选人策略直接使用 System 组织数据；
5. 当前登录用户通过 Security 工具获取；
6. 前端 `v-hasPermi`、动态菜单、字典和用户选择组件；
7. 流程通知依赖平台消息与企业微信能力。

AgentTo 当前先维护自己的账号和组织数据，后续再接企业微信。因此迁移时建议先定义以下接口：

- `IdentityGateway`：当前用户、用户详情；
- `OrganizationGateway`：部门、岗位、角色和负责人；
- `WorkflowCandidateResolver`：把候选策略解析为用户集合；
- `PermissionService`：平台权限校验；
- `NotificationGateway`：站内/企微通知；
- `FileStorageGateway`：文件读取和临时 URL。

HopFresh 的候选人算法可以放在 `WorkflowCandidateResolver` 的实现中，但不能继续直接调用它的 System API。

是否需要多租户：**未确认**。当前业务描述是公司内部固定高管和秘书组，没有足够证据证明第一阶段需要 HopFresh 的完整多租户体系。

## 14. 推荐迁移顺序

### 阶段一：先定边界，不搬业务代码

1. 保留 `WorkflowGateway`；
2. 补齐流程模型、定义、实例、任务和事件所需接口；
3. 定义组织、通知、文件、权限适配口；
4. 明确公司现成工作流代码是否就是 HopFresh 这一套，以及是否已经接企微。

退出条件：能确定真正采用 Flowable/HopFresh 还是公司另一套流程服务。

### 阶段二：做 BPM 最小闭环兼容验证

只验证：

`流程定义/部署 → AgentTo 发起 → 创建人工任务 → 通过/驳回/退回 → 状态事件 → 回写 at_task → 历史查询`

这一阶段不接工程项目表，不接 AI，也不迁移全部页面。

重点验证：

- Spring Boot 4.0.6 + Java 21 + Flowable 7.2.0；
- Flowable schema 初始化；
- `WorkflowGateway` 适配；
- 用户 ID 类型；
- 事务边界和重复事件幂等；
- 流程结果和 `at_*` 业务表一致性。

### 阶段三：补模型、表单、审批动作和管理页面

迁移通用 BPM 表和前端页面，逐步加入：

- BPMN 设计、表单、部署和版本；
- 待办、已办、实例详情；
- 通过、驳回、退回、转办、委派、加签、减签、撤回；
- 通知和历史；
- 权限和组织适配。

每增加一个动作，都要增加接口、数据库和流程历史断言，不按页面按钮是否出现来判断完成。

### 阶段四：接 Agent 能力

1. 建立模型/密钥配置；
2. 基于 Spring AI 2.0 实现 Agent 运行接口；
3. 主后端通过 HTTP 调独立 RAG；
4. 拼装任务、文件版本、角色、节点、历史咨询意见和知识引用；
5. 在自审和秘书组节点接入，结果先按各自权限可见；
6. 保存 Prompt 版本、模型信息、输入引用、输出和人工处理结果。

### 阶段五：接企微和公司现有平台能力

最后再把 `NotificationGateway` 和身份组织适配器指向公司现有企微代码，避免消息接入反过来影响流程核心。

## 15. 主要风险

| 风险 | 影响 | 建议 |
|---|---|---|
| 工作流业务细节仍未最终确认 | 可能导致 BPMN 和任务动作反复调整 | 先做引擎边界和通用能力，不固化 C 意见的业务含义 |
| 公司现成工作流代码尚未拿到 | 可能重复建设 | 在正式迁移前确认代码位置、引擎、企微接入和维护人 |
| Boot 4.0.6 与 Flowable 7.2 兼容性未验证 | 项目可能无法启动或运行时异常 | 单独做最小兼容验证并固定依赖 |
| HopFresh 使用 Flowable 内部 API | 升级 Flowable 后加签/退回/撤回易出问题 | 锁版本并为每种任务动作写集成测试 |
| Spring AI 1.1 → 2.0 差异 | AI 代码无法直接编译 | 复用业务思想和数据结构，按 2.0 重写适配层 |
| System/Infra/租户耦合 | 搬一项功能带入大量平台模块 | 先建 Gateway，再移核心逻辑 |
| 数据库 DDL 不完整 | 无法可靠初始化 | 使用官方 Flowable schema 或受控库导出，纳入 Flyway |
| 前后端接口存在缺口 | 页面可见但运行报错 | 建契约测试和端到端用例 |
| 多个 ID 体系不同 | 用户、任务、流程映射错误 | 统一内部 ID，外部 ID 放 mapping 表 |
| AI 密钥和文件正文安全 | 可能泄密 | 密钥加密、权限过滤、正文出域策略和完整审计 |
| 流程事件重复或顺序异常 | 业务状态被重复回写 | 事件幂等键、状态机校验、事务外盒或重试表 |
| 代码授权和依赖许可证未确认 | 无法正式交付 | 迁移前完成公司内部授权与依赖清单审查 |

## 16. 复用矩阵

| 能力 | HopFresh 实现位置 | 当前项目现状 | 复用方式 | 依赖 | 风险 | 证据 |
|---|---|---|---|---|---|---|
| 流程模型与部署 | `BpmModelServiceImpl`、`BpmProcessDefinitionServiceImpl` | 无真实引擎 | 适配复用 | Flowable、表单、候选人、DB | Boot/Flowable 兼容、DDL | `BpmModelServiceImpl.java:215`；`BpmProcessDefinitionServiceImpl.java:135` |
| BPMN 前端设计 | `views/bpm/model/form/editor`、bpmn-js | 主前端未开始 | 轻量复用页面与交互 | Vue、bpmn-js、权限组件 | 前端版本、样式和许可证 | `hopfresh-web/package.json:106`；model 页面源码 |
| 简易流程设计器 | BPM SIMPLE 模型与转换工具 | 无 | 观察后适配 | HopFresh 自定义节点模型 | 与最终业务流程是否匹配未确认 | `SimpleModelUtils`、模型 Service |
| 动态表单 | `BpmFormController/Service`、FormCreate 页面 | AgentTo 当前以文件为主 | 可选适配 | FormCreate、字段权限 | 可能超出第一阶段需要 | `BpmFormController.java`；`views/bpm/form` |
| 跨模块发起流程 | `BpmProcessInstanceApi` | 已有 `WorkflowGateway` | 采用接口形态，在 Gateway 后实现 | 引擎、用户、businessKey | 用户 ID 和事务边界 | `BpmProcessInstanceApi.java`；`WorkflowGateway.java` |
| 流程实例创建 | `BpmProcessInstanceServiceImpl` | 内存模拟 | 适配复用 | RuntimeService、流程定义 | 变量、指定审批人校验 | `BpmProcessInstanceServiceImpl.java:760-834` |
| 待办/已办/历史 | `BpmTaskController/Service`、前端 task 页面 | 无 | 适配复用 | Flowable Task/History | 权限和数据范围 | `BpmTaskController.java`；`views/bpm/task` |
| 通过/驳回 | `BpmTaskServiceImpl` | 尚无真实动作 | 适配复用 | Flowable TaskService | 状态回写、重复操作 | `BpmTaskServiceImpl.java:699,984` |
| 退回/撤回 | `BpmTaskServiceImpl` | 业务规则待确认 | 引擎能力可复用，业务规则后配 | ChangeActivityStateBuilder | 目标节点和历史完整性 | `BpmTaskServiceImpl.java:1054,1526` |
| 转办/委派 | `BpmTaskServiceImpl` | 未实现 | 适配复用 | 用户组织、TaskService | 角色含义混淆 | `BpmTaskServiceImpl.java:1268,1299` |
| 加签/减签 | `BpmTaskServiceImpl` | 未实现 | 谨慎适配 | Flowable 内部 TaskEntity | 强版本耦合 | `BpmTaskServiceImpl.java:1369,1487` |
| 抄送 | `bpm_process_instance_copy` | 可由参与人/通知实现 | 适配复用 | 自有表、用户体系 | 与 RACI 的 I 是否等同未确认 | `BpmProcessInstanceCopyDO.java` |
| 候选人策略 | 16 类策略、自定义 UserTask Behavior | A/C/秘书组/老板角色待落库 | 适配核心算法 | System 用户部门岗位角色 | HopFresh 组织耦合 | `BpmTaskCandidateStrategyEnum`；`BpmUserTaskActivityBehavior` |
| 流程状态事件 | `BpmProcessInstanceStatusEventListener` | 有审计表，尚无引擎事件 | 直接采用设计模式 | Spring Event | 幂等和事务 | 状态事件类；OA Listener 样例 |
| 业务结果回写 | OA/工程/项目审批样例 | `at_task`、`at_workflow_mapping` | 只复用模式 | 业务 Service、mapping | 不能搬业务表 | `BpmOALeaveServiceImpl.java:47-70` |
| 企微通知 | HopFresh/平台相关模块 | 公司可能有现成代码 | 通过 NotificationGateway 适配 | 企微应用、消息模板 | 现成代码位置未确认 | BPM POM 和消息 Service；运行配置未确认 |
| API Key 管理 | `AiApiKeyController/Service` | DeepSeek 目前由环境变量配置 | 适配复用 | 加密、权限、DB | 明文存储/回显风险 | `AiApiKeyController.java`；`ai_api_key` |
| 模型管理与工厂 | `AiModelServiceImpl`、`AiModelFactoryImpl` | Spring AI 2.0，模型需可插拔 | 重写 2.0 适配，复用配置思想 | 模型 SDK/API | 1.1→2.0 差异 | `AiModelFactoryImpl.java:141-334` |
| 流式会话 | `AiChatMessageServiceImpl`、chat 页面 | 尚未形成 Agent 流式运行 | 适配复用 | SSE、ChatModel、DB | 通用聊天模型不符合任务上下文 | `AiChatMessageServiceImpl.java:195-495` |
| Prompt/角色 | `ai_chat_role`、消息组装 | 文档已定义动态 Prompt 组成 | 适配数据结构和页面 | 任务/角色/历史/RAG | Prompt 版本和可追溯性 | `AiChatMessageServiceImpl.java:299-381` |
| Tool/MCP | Chat ToolCallback/MCP client | 未实现 | 后续适配 | MCP、工具权限 | 工具越权和审计 | `AiChatMessageServiceImpl.java:384-418` |
| 文件附件 | `Infra FileApi` + `readUrl` | 主系统计划 MinIO，RAG 已接 MinIO | 替换为 FileStorageGateway | MinIO、临时 URL | 文件权限和正文出域 | `AiChatMessageServiceImpl.java:461-495` |
| 联网搜索 | Bocha | 当前非核心 | 暂不迁移 | Bocha API | 文件场景可能不允许联网 | Chat Service 搜索分支 |
| HopFresh RAG | Tika、SimpleVectorStore、可选 Rerank | 已有独立 RAG | 不迁移核心，只参考管理思路 | FileApi、VectorStore、Embedding | 功能倒退、重复建设 | `AiModelServiceImpl.java:128-173`；知识 Service |
| AgentTo RAG | 不在 HopFresh | DOCX 结构切分、ES 双路、RRF、TEI、去重、Trace | 保留为独立服务 | MySQL、MinIO、ES、TEI | 服务契约和权限过滤仍需完善 | `HybridRetrievalService.java`；`StructureAwareChunker.java`；`QueryTraceService.java` |
| TinyFlow | `AiWorkflowController/Service`、workflow 页面 | 无，当前也不需要 | 暂不迁移 | TinyFlow、模型 | 仅两类 provider；与 BPM 混淆 | `AiWorkflowServiceImpl.java:42-141` |
| 图像/音乐/写作/思维导图 | AI 对应模块和页面 | 不在项目范围 | 不迁移 | 多家模型 API | 扩大范围、部分链路不完整 | 对应 Controller/Service/DO；前端审计证据 |

## 17. 仍需确认的问题

以下问题不是通过当前源码就能得出答案：

1. 公司所谓“已经接好企微的工作流代码”是否就是 HopFresh 的 BPM 模块；
2. 该代码当前的实际部署方式、Nacos 配置、数据库版本和维护人；
3. 公司希望复用到什么程度，是允许复制源码，还是只能参考实现；
4. Flowable 7.2.0 是否必须保持，还是允许在 AgentTo 中重新选版本；
5. 现有 Flowable 数据是否需要迁移，还是 AgentTo 使用全新库；
6. 是否需要 HopFresh 的在线表单，还是第一阶段只处理文件和意见；
7. 哪些审批动作是 AgentTo 首期必须保留的；
8. 是否需要多租户；
9. 用户、部门、岗位、角色的最终来源和 ID 规范；
10. 企微通知只推待办，还是还要在企微内直接办理；
11. AI 模型密钥是否允许平台管理员维护，还是只能走服务器环境变量/密钥服务；
12. HopFresh 源码和第三方组件的正式授权边界。

在这些问题确认前，可以继续做源码拆分和兼容验证设计，但不建议开始大规模代码迁移。

## 18. 主要源码证据索引

### BPM 后端

- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-api\src\main\java\cn\iocoder\yudao\module\bpm\api\task\BpmProcessInstanceApi.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-api\src\main\java\cn\iocoder\yudao\module\bpm\api\event\BpmProcessInstanceStatusEventListener.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\service\definition\BpmModelServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\service\definition\BpmProcessDefinitionServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\service\task\BpmProcessInstanceServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\service\task\BpmTaskServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\framework\flowable\config\BpmFlowableConfiguration.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\framework\flowable\core\behavior\BpmActivityBehaviorFactory.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\framework\flowable\core\listener\BpmTaskEventListener.java`
- `E:\hopfresh\hopfresh-server\yudao-module-bpm\yudao-module-bpm-server\src\main\java\cn\iocoder\yudao\module\bpm\framework\flowable\core\listener\BpmProcessInstanceEventListener.java`

### AI 后端

- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-api\src\main\java\cn\iocoder\yudao\module\ai\enums\model\AiPlatformEnum.java`
- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-server\src\main\java\cn\iocoder\yudao\module\ai\framework\ai\core\model\AiModelFactoryImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-server\src\main\java\cn\iocoder\yudao\module\ai\service\model\AiModelServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-server\src\main\java\cn\iocoder\yudao\module\ai\service\chat\AiChatMessageServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-server\src\main\java\cn\iocoder\yudao\module\ai\service\knowledge\AiKnowledgeDocumentServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-server\src\main\java\cn\iocoder\yudao\module\ai\service\knowledge\AiKnowledgeSegmentServiceImpl.java`
- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-server\src\main\java\cn\iocoder\yudao\module\ai\service\knowledge\splitter\SemanticTextSplitter.java`
- `E:\hopfresh\hopfresh-server\yudao-module-ai\yudao-module-ai-server\src\main\java\cn\iocoder\yudao\module\ai\service\workflow\AiWorkflowServiceImpl.java`

### 前端

- `E:\hopfresh\hopfresh-web\src\views\bpm`
- `E:\hopfresh\hopfresh-web\src\api\bpm`
- `E:\hopfresh\hopfresh-web\src\views\ai`
- `E:\hopfresh\hopfresh-web\src\api\ai`

### AgentTo 当前实现

- `E:\AgentTo\backend\src\main\java\com\agentto\platform\workflow\application\WorkflowGateway.java`
- `E:\AgentTo\backend\src\main\java\com\agentto\platform\workflow\infrastructure\InMemoryWorkflowGateway.java`
- `E:\AgentTo\backend\src\main\resources\db\migration\V1__create_minimum_task_tables.sql`
- `E:\AgentTo\rag-service\backend\src\main\java\com\agentto\rag\ingestion\chunk\StructureAwareChunker.java`
- `E:\AgentTo\rag-service\backend\src\main\java\com\agentto\rag\retrieval\HybridRetrievalService.java`
- `E:\AgentTo\rag-service\backend\src\main\java\com\agentto\rag\index\ElasticsearchChunkIndex.java`
- `E:\AgentTo\rag-service\backend\src\main\java\com\agentto\rag\embedding\TeiEmbeddingClient.java`
- `E:\AgentTo\rag-service\backend\src\main\java\com\agentto\rag\retrieval\TeiRerankClient.java`
- `E:\AgentTo\rag-service\backend\src\main\java\com\agentto\rag\retrieval\ContentDeduplicator.java`
- `E:\AgentTo\rag-service\backend\src\main\java\com\agentto\rag\retrieval\QueryTraceService.java`
