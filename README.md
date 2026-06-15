<<<<<<< HEAD
# ai-learning-system
AI-powered personalized learning system with multi-agent architecture and RAG-based knowledge retrieval for intelligent education assistance.
=======
# 工程化 AI 学习辅助系统

中国软件杯 A3 方向的可运行 MVP。系统由 Spring Boot 业务后端、FastAPI/LangGraph AI
服务、MySQL 与 FAISS 向量检索组成。

## 架构

```text
Client
  |
  v
Spring Boot 8080
  |-- 用户、会话、学习任务、记录、题目、结果、画像
  |-- MySQL/H2
  |
  +-- HTTP/JSON --> FastAPI 8000
                       |
                       +-- LangGraph
                       |    PlannerAgent -> ResourceAgent -> ContentAgent -> QuizAgent
                       |    EvaluationAgent -> ProfileUpdater -> 条件补救路径
                       |    TutorAgent -> 意图路由
                       |
                       +-- Tool Registry
                       |    search_knowledge_base
                       |    generate_learning_plan
                       |    generate_quiz
                       |    evaluate_answer
                       |    retrieve_user_profile
                       |
                       +-- BGE/Hash Embedding -> FAISS
                       +-- LLMProvider -> DeepSeek/OpenAI/Qwen/Mock
```

Agent 只经 Tool Registry 使用外部能力。LangGraph 状态由 `task_id` 对应的 `thread_id`
隔离，并使用 Checkpointer 保存单次服务进程内的执行状态。Java 是业务数据唯一持久化入口。

## 快速启动

最省事的方式：

```bash
docker compose up --build
```

启动完成后访问：

- 前端页面：`http://localhost`
- 后端 API：`http://localhost:8080`
- AI 服务文档：`http://localhost:8000/docs`

本地开发：

```bash
cd ai-service
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt
.venv/Scripts/uvicorn app.main:app --reload
```

```bash
cd backend
mvn spring-boot:run
```

后端默认使用文件型 H2，无需先启动 MySQL。演示账号为 `demo / demo123`。
AI 服务默认使用 `mock` Provider，可在没有 API Key 时跑通完整闭环。

## 模型配置

复制 `ai-service/.env.example` 为 `.env`。三种服务均通过 OpenAI Compatible API 接入：

| Provider | `LLM_PROVIDER` | 默认 Base URL |
|---|---|---|
| DeepSeek | `deepseek` | `https://api.deepseek.com/v1` |
| OpenAI | `openai` | `https://api.openai.com/v1` |
| Qwen | `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` |

同时设置 `LLM_MODEL` 和 `LLM_API_KEY`。`USE_BGE=true` 会加载
`BAAI/bge-small-zh-v1.5`；默认使用本地 Hash Embedding，便于离线演示。

数字教师语音使用 Gemini TTS：

```env
GEMINI_API_KEY=your-key
GEMINI_TTS_MODEL=gemini-3.1-flash-preview-tts
GEMINI_TTS_VOICE=Sulafat
```

未配置 Gemini Key 或调用失败时，前端自动降级为浏览器系统中文语音。

## 核心流程

1. 登录：

```http
POST /api/auth/login
Content-Type: application/json

{"username":"demo","password":"demo123"}
```

2. 携带返回的 Bearer Token 创建学习任务：

```http
POST /api/learning/tasks
Authorization: Bearer <token>
Content-Type: application/json

{"topic":"Java并发编程","goal":"理解线程安全并能分析竞态条件"}
```

响应包含学习计划、RAG 资源、教学内容和不含答案的练习题。

3. 提交答案：

```http
POST /api/learning/tasks/1/answers
Authorization: Bearer <token>
Content-Type: application/json

{
  "answers": [
    {"quizId":"<quiz-id-1>","answer":"A"},
    {"quizId":"<quiz-id-2>","answer":"正确"},
    {"quizId":"<quiz-id-3>","answer":"Java并发的概念、作用和应用"}
  ]
}
```

评估低于 85 分时，LangGraph 路由到补救学习节点并返回下一步计划；同时按
`新掌握度 = 旧值 * 0.6 + 本次值 * 0.4` 更新画像。

## Python API

| 方法 | 路径 | 功能 |
|---|---|---|
| `POST` | `/ai/v1/plans/generate` | 学习计划、资源、内容与题目生成 |
| `POST` | `/ai/v1/evaluations/answer` | 判分、反馈、画像更新与条件路由 |
| `POST` | `/ai/v1/tutor/chat` | TutorAgent 对话路由 |
| `POST` | `/ai/v1/knowledge/documents` | 上传 PDF/DOCX/TXT/Markdown |
| `POST` | `/ai/v1/knowledge/search` | 向量检索 |
| `GET` | `/health` | 健康检查 |

FastAPI 启动后可访问 `http://localhost:8000/docs` 调试。

## 目录

```text
ai-learning-system/
|-- backend/                    Spring Boot 业务后端
|-- ai-service/
|   |-- app/graphs/             LangGraph 工作流
|   |-- app/tools/              Tool Registry
|   |-- app/rag/                文档解析、切分、Embedding、FAISS
|   |-- app/llm/                统一 LLMProvider
|   |-- app/memory/             用户画像客户端
|   `-- tests/
|-- docker-compose.yml
`-- README.md
```

## MVP 边界

- Checkpointer 当前为进程内存，生产化可替换为数据库 Checkpointer。
- Session Token 当前存于 Java 进程内存，适合比赛演示；生产化应替换为 JWT/Redis。
- 文档元数据与向量索引当前为单机文件，后续可将 `RagService` 替换为 Milvus。
- 主观题 MVP 使用规则评分；接入真实 LLM 后可增加 Rubric 二次评分与一致性校验。

>>>>>>> f4096cd (init A3 project)
