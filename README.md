# 工程化 AI 学习辅助系统

中国软件杯 A3 方向的个性化学习辅助系统。项目以“诊断、规划、学习、测评、调整”为核心闭环，集成多智能体工作流、RAG 知识检索、AI 助教、数字教师和人脸登录。

## 功能

- 根据学习主题和目标生成个性化学习路径
- 生成学习内容、参考资料和练习题
- 自动评估答案并更新学习画像
- AI 助教完成知识问答、出题和学习规划
- 数字教师进行语音讲解、字幕同步和语速控制
- PDF、DOCX、TXT、Markdown 知识库检索
- 密码登录、人脸录入和人脸验证登录
- 登录 Token 持久化，后端重启后会话仍可继续使用

## 架构

```text
Browser
  |
  v
Nginx / Vue 3
  |
  v
Spring Boot 8080
  |-- 认证、会话、用户画像
  |-- 学习任务、题目、测评记录
  |-- MySQL / H2
  |
  +-- Docker 内网 HTTP --> FastAPI 8000
                               |
                               +-- LangGraph 多智能体
                               +-- Tool Registry
                               +-- RAG + FAISS
                               +-- DeepSeek / OpenAI / Qwen / Mock
                               +-- Gemini TTS
```

Spring Boot 是业务数据入口。Python 服务负责 AI 推理、工作流编排、知识检索和语音合成，不直接暴露到公网。

## 快速启动

在项目根目录创建 `.env`：

```env
LLM_PROVIDER=mock
LLM_MODEL=mock-learning-model
LLM_API_KEY=
LLM_BASE_URL=

GEMINI_API_KEY=
GEMINI_TTS_MODEL=gemini-3.1-flash-preview-tts
GEMINI_TTS_VOICE=Sulafat

USE_BGE=false
```

启动全部服务：

```bash
docker compose up -d --build
```

默认地址：

- 前端：`http://localhost:8081`
- 后端 API：`http://localhost:8080`
- MySQL：`localhost:3306`
- AI 服务：仅允许 Docker 内网通过 `http://ai-service:8000` 访问

演示账号：

```text
demo / demo123
```

## 模型配置

### 大语言模型

Python 服务通过 OpenAI Compatible API 接入模型：

| Provider | `LLM_PROVIDER` | 默认 Base URL |
|---|---|---|
| DeepSeek | `deepseek` | `https://api.deepseek.com/v1` |
| OpenAI | `openai` | `https://api.openai.com/v1` |
| 通义千问 | `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| 离线演示 | `mock` | 不调用外部模型 |

示例：

```env
LLM_PROVIDER=deepseek
LLM_MODEL=deepseek-chat
LLM_API_KEY=your-key
```

未配置 API Key 时自动使用 Mock Provider。

### 向量模型

默认使用本地 384 维 Hash Embedding。启用 BGE：

```env
USE_BGE=true
EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
```

### 数字教师语音

数字教师使用 Gemini TTS：

```env
GEMINI_API_KEY=your-key
GEMINI_TTS_MODEL=gemini-3.1-flash-preview-tts
GEMINI_TTS_VOICE=Sulafat
```

Gemini 未配置或调用失败时，前端自动降级为浏览器系统中文语音。

### 人脸识别

人脸识别运行在浏览器本地，使用 `@vladmandic/human`：

- BlazeFace：人脸检测
- FaceRes：人脸特征提取
- AntiSpoof：防照片和翻拍检测
- Liveness：活体检测

服务器只保存人脸特征向量，不保存摄像头照片。

## 核心流程

1. 用户通过密码登录并录入人脸。
2. 输入学习主题和目标。
3. LangGraph 生成学习计划、内容、资料和练习。
4. 用户提交答案，系统评估结果并更新学习画像。
5. AI 助教基于当前任务和画像提供针对性回答。
6. 数字教师调用 Gemini TTS，将回答转换为语音讲解。

## 主要接口

### Spring Boot

| 方法 | 路径 | 功能 |
|---|---|---|
| `POST` | `/api/auth/login` | 密码登录 |
| `POST` | `/api/auth/face/enroll` | 录入或更新人脸 |
| `POST` | `/api/auth/face/login` | 人脸登录 |
| `POST` | `/api/learning/tasks` | 创建学习任务 |
| `POST` | `/api/learning/tasks/{id}/answers` | 提交答案 |
| `POST` | `/api/tutor/chat` | AI 助教问答 |
| `POST` | `/api/tutor/speech` | 数字教师语音 |
| `GET` | `/api/users/me/profile` | 获取学习画像 |

### FastAPI

| 方法 | 路径 | 功能 |
|---|---|---|
| `POST` | `/ai/v1/plans/generate` | 生成学习计划和内容 |
| `POST` | `/ai/v1/evaluations/answer` | 评估答案 |
| `POST` | `/ai/v1/tutor/chat` | 助教意图路由和回答 |
| `POST` | `/ai/v1/tts` | Gemini 语音合成 |
| `POST` | `/ai/v1/knowledge/search` | 知识库检索 |
| `POST` | `/ai/v1/knowledge/documents` | 上传知识文档 |
| `GET` | `/health` | 健康检查 |

## 本地开发

前端：

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。

后端：

```bash
cd backend
mvn spring-boot:run
```

本地默认使用文件型 H2。

AI 服务：

```bash
cd ai-service
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt
.venv/Scripts/uvicorn app.main:app --reload
```

## 目录

```text
ai-learning-system/
|-- frontend/                  Vue 3、数字教师、人脸识别
|-- backend/                   Spring Boot 业务服务
|-- ai-service/
|   |-- app/graphs/            LangGraph 工作流
|   |-- app/tools/             工具注册与调用
|   |-- app/rag/               文档解析和向量检索
|   |-- app/llm/               大模型 Provider
|   |-- app/tts/               Gemini TTS
|   `-- tests/
|-- docker-compose.yml
`-- README.md
```

## 当前边界

- LangGraph Checkpointer 当前为进程内存。
- RAG 文档和向量索引使用单机文件卷，尚未接入 Milvus。
- 主观题仍以规则评分为主，可继续增加 Rubric 和模型复核。
- 数字教师目前是 2D 形象与语音驱动动画，尚未接入视频级口型模型。
- 正式部署摄像头功能时必须使用 HTTPS。
