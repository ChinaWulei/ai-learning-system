<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { api, clearSession, getSession, saveSession } from "./api";
import {
  captureFaceEmbedding,
  closeCamera,
  openCamera,
} from "./faceRecognition";

const session = ref(getSession());
const activeView = ref("learn");
const busy = ref(false);
const error = ref("");
const task = ref(null);
const profile = ref(null);
const evaluation = ref(null);
const answers = ref({});
const topic = ref("");
const goal = ref("");
const tutorMessage = ref("");
const messages = ref([
  {
    role: "assistant",
    content: "你好，我是你的 AI 学习助教。可以问我知识点，也可以让我生成练习或学习计划。",
  },
]);
const loginForm = ref({ username: "demo", password: "demo123" });
const loginMode = ref("password");
const faceActive = ref(false);
const faceStatus = ref("");
const faceVideo = ref(null);
let cameraStream = null;

const navItems = [
  { id: "learn", label: "学习中心", icon: "◫" },
  { id: "tutor", label: "AI 助教", icon: "✦" },
  { id: "profile", label: "学习画像", icon: "◎" },
];

const stages = computed(() => task.value?.learning_plan?.stages || []);
const masteryEntries = computed(() => Object.entries(profile.value?.mastery || {}));

function textValue(value) {
  if (value === null || value === undefined) return "";
  return typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

function questionType(type) {
  return {
    single_choice: "单选题",
    true_false: "判断题",
    short_answer: "简答题",
  }[type] || "练习题";
}

async function login() {
  error.value = "";
  busy.value = true;
  try {
    const data = await api.login(loginForm.value.username, loginForm.value.password);
    saveSession(data);
    session.value = getSession();
    await loadProfile();
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
}

async function runFaceAction(action) {
  error.value = "";
  faceStatus.value = "";
  if (!loginForm.value.username.trim()) {
    error.value = "请先输入用户名";
    return;
  }
  if (action === "enroll" && !loginForm.value.password) {
    error.value = "录入人脸前请输入密码";
    return;
  }

  busy.value = true;
  faceActive.value = true;
  try {
    await nextTick();
    cameraStream = await openCamera(faceVideo.value);
    const embedding = await captureFaceEmbedding(
      faceVideo.value,
      (status) => { faceStatus.value = status; },
    );
    if (action === "enroll") {
      await api.enrollFace(
        loginForm.value.username.trim(),
        loginForm.value.password,
        embedding,
      );
      faceStatus.value = "人脸录入成功，现在可以刷脸登录";
      loginMode.value = "face";
    } else {
      const data = await api.faceLogin(loginForm.value.username.trim(), embedding);
      saveSession(data);
      session.value = getSession();
      await loadProfile();
    }
  } catch (err) {
    error.value = err.name === "NotAllowedError"
      ? "摄像头权限被拒绝，请在浏览器设置中允许访问"
      : err.message;
  } finally {
    closeCamera(cameraStream);
    cameraStream = null;
    faceActive.value = false;
    busy.value = false;
  }
}

function cancelFaceCapture() {
  closeCamera(cameraStream);
  cameraStream = null;
  faceActive.value = false;
  busy.value = false;
  faceStatus.value = "";
}

function logout() {
  clearSession();
  session.value = getSession();
  task.value = null;
  profile.value = null;
}

async function loadProfile() {
  if (!session.value.token) return;
  try {
    profile.value = await api.profile();
  } catch (err) {
    error.value = err.message;
  }
}

async function createTask() {
  if (!topic.value.trim()) return;
  error.value = "";
  evaluation.value = null;
  busy.value = true;
  try {
    task.value = await api.createTask(topic.value.trim(), goal.value.trim());
    answers.value = {};
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
}

async function submitQuiz() {
  const quizzes = task.value?.quizzes || [];
  if (quizzes.some((quiz) => !String(answers.value[quiz.id] || "").trim())) {
    error.value = "请完成全部题目后再提交。";
    return;
  }
  error.value = "";
  busy.value = true;
  try {
    evaluation.value = await api.submitAnswers(
      task.value.id,
      quizzes.map((quiz) => ({
        quizId: quiz.id,
        answer: String(answers.value[quiz.id]),
      })),
    );
    await loadProfile();
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
}

async function askTutor() {
  const content = tutorMessage.value.trim();
  if (!content || busy.value) return;
  messages.value.push({ role: "user", content });
  tutorMessage.value = "";
  busy.value = true;
  try {
    const result = await api.tutor(content, task.value?.id);
    messages.value.push({
      role: "assistant",
      content: result.answer || textValue(result),
    });
  } catch (err) {
    messages.value.push({ role: "assistant", content: `请求失败：${err.message}` });
  } finally {
    busy.value = false;
  }
}

function handleSessionExpired() {
  session.value = getSession();
  error.value = "登录状态已失效，请重新登录。";
}

onMounted(() => {
  window.addEventListener("session-expired", handleSessionExpired);
  loadProfile();
});
onBeforeUnmount(() => {
  window.removeEventListener("session-expired", handleSessionExpired);
  closeCamera(cameraStream);
});
</script>

<template>
  <main v-if="!session.token" class="login-shell">
    <section class="login-brand">
      <div class="brand-mark">知</div>
      <p class="eyebrow">AI LEARNING SYSTEM</p>
      <h1>让每一次学习<br />都有清晰的方向</h1>
      <p class="brand-copy">
        基于你的目标与学习画像，动态规划路径、生成内容并评估掌握程度。
      </p>
      <div class="brand-orbit orbit-one"></div>
      <div class="brand-orbit orbit-two"></div>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="loginMode === 'password' ? login() : runFaceAction('login')">
        <div>
          <p class="eyebrow">WELCOME BACK</p>
          <h2>登录学习空间</h2>
          <p class="muted">密码登录后可录入人脸，下次直接刷脸进入</p>
        </div>
        <div class="login-tabs" role="tablist" aria-label="登录方式">
          <button
            type="button"
            :class="{ active: loginMode === 'password' }"
            @click="loginMode = 'password'; error = ''"
          >密码登录</button>
          <button
            type="button"
            :class="{ active: loginMode === 'face' }"
            @click="loginMode = 'face'; error = ''"
          >人脸登录</button>
        </div>
        <label>
          <span>用户名</span>
          <input v-model="loginForm.username" autocomplete="username" />
        </label>
        <label v-if="loginMode === 'password'">
          <span>密码</span>
          <input v-model="loginForm.password" type="password" autocomplete="current-password" />
        </label>
        <div v-if="faceActive" class="face-camera">
          <video ref="faceVideo" playsinline muted></video>
          <div class="face-guide" aria-hidden="true"></div>
          <p>{{ faceStatus }}</p>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button v-if="!faceActive" class="primary-button" :disabled="busy">
          {{ busy
            ? "正在处理..."
            : loginMode === "password" ? "进入学习空间" : "开始人脸验证" }}
        </button>
        <button
          v-else
          type="button"
          class="secondary-button"
          @click="cancelFaceCapture"
        >取消验证</button>
        <button
          v-if="loginMode === 'password' && !faceActive"
          type="button"
          class="secondary-button"
          :disabled="busy"
          @click="runFaceAction('enroll')"
        >
          录入或更新人脸
        </button>
        <p class="demo-tip">演示账号：demo / demo123</p>
      </form>
    </section>
  </main>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="logo"><span>知</span><strong>知行</strong></div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item.id"
          :class="{ active: activeView === item.id }"
          @click="activeView = item.id"
        >
          <span class="nav-icon">{{ item.icon }}</span>{{ item.label }}
        </button>
      </nav>
      <div class="sidebar-user">
        <div class="avatar">{{ session.user?.nickname?.slice(0, 1) || "学" }}</div>
        <div><strong>{{ session.user?.nickname }}</strong><small>持续学习中</small></div>
        <button class="logout" title="退出登录" @click="logout">↗</button>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">PERSONAL LEARNING SPACE</p>
          <h2>{{ navItems.find((item) => item.id === activeView)?.label }}</h2>
        </div>
        <div class="status-pill"><i></i> AI 服务已连接</div>
      </header>

      <p v-if="error" class="global-error" @click="error = ''">{{ error }} <span>×</span></p>

      <div v-if="activeView === 'learn'" class="view">
        <section v-if="!task" class="hero-grid">
          <div class="hero-copy">
            <p class="eyebrow">START A NEW JOURNEY</p>
            <h1>今天，想掌握<br /><em>什么新知识？</em></h1>
            <p>告诉 AI 你的学习主题和目标，我们将为你生成专属路径、内容与练习。</p>
          </div>
          <form class="create-card" @submit.prevent="createTask">
            <label>
              <span>学习主题</span>
              <input v-model="topic" placeholder="例如：Java 并发编程" />
            </label>
            <label>
              <span>学习目标 <small>选填</small></span>
              <textarea
                v-model="goal"
                rows="4"
                placeholder="例如：理解线程安全，并能分析常见竞态条件"
              ></textarea>
            </label>
            <button class="primary-button" :disabled="busy || !topic.trim()">
              {{ busy ? "AI 正在规划..." : "生成我的学习路径" }} <span>→</span>
            </button>
          </form>
        </section>

        <template v-else>
          <section class="task-heading">
            <div>
              <button class="text-button" @click="task = null">← 新建主题</button>
              <h1>{{ task.topic }}</h1>
              <p>{{ task.goal || "掌握核心知识并完成基础应用" }}</p>
            </div>
            <div class="task-status">
              <span>{{ task.status }}</span>
              <strong>{{ task.learning_plan?.estimated_hours || 0 }}h</strong>
              <small>预计学习时长</small>
            </div>
          </section>

          <section v-if="stages.length" class="panel">
            <div class="section-title">
              <div><p class="eyebrow">LEARNING PATH</p><h3>学习路径</h3></div>
              <span>{{ stages.length }} 个阶段</span>
            </div>
            <div class="timeline">
              <article v-for="(stage, index) in stages" :key="stage.order || index">
                <div class="step">{{ String(index + 1).padStart(2, "0") }}</div>
                <div><h4>{{ stage.name }}</h4><p>{{ stage.objective }}</p></div>
                <strong>{{ stage.hours }}h</strong>
              </article>
            </div>
          </section>

          <section class="content-grid">
            <div class="panel">
              <div class="section-title">
                <div><p class="eyebrow">COURSE NOTES</p><h3>学习内容</h3></div>
              </div>
              <article v-for="(item, index) in task.content || []" :key="index" class="lesson">
                <h4>{{ item.title || `内容 ${index + 1}` }}</h4>
                <p>{{ item.body }}</p>
                <span v-if="item.grounded" class="source-tag">知识库内容</span>
              </article>
              <p v-if="!task.content?.length" class="empty">暂无学习内容</p>
            </div>
            <div class="panel resource-panel">
              <div class="section-title">
                <div><p class="eyebrow">RESOURCES</p><h3>参考资料</h3></div>
              </div>
              <article v-for="(item, index) in task.resources || []" :key="index">
                <span>{{ String(index + 1).padStart(2, "0") }}</span>
                <p>{{ item.content || item.title || textValue(item) }}</p>
              </article>
              <p v-if="!task.resources?.length" class="empty">知识库暂无相关资料</p>
            </div>
          </section>

          <section v-if="task.quizzes?.length" class="panel quiz-panel">
            <div class="section-title">
              <div><p class="eyebrow">KNOWLEDGE CHECK</p><h3>掌握度测验</h3></div>
              <span>{{ task.quizzes.length }} 道题</span>
            </div>
            <article v-for="(quiz, index) in task.quizzes" :key="quiz.id" class="question">
              <div class="question-meta">
                <span>Q{{ index + 1 }}</span>
                <small>{{ questionType(quiz.type) }} · {{ quiz.difficulty }}</small>
              </div>
              <h4>{{ quiz.question }}</h4>
              <div v-if="quiz.options?.length" class="options">
                <label v-for="(option, optionIndex) in quiz.options" :key="optionIndex">
                  <input
                    v-model="answers[quiz.id]"
                    type="radio"
                    :name="quiz.id"
                    :value="quiz.type === 'single_choice'
                      ? String.fromCharCode(65 + optionIndex)
                      : option"
                  />
                  <span class="option-key">{{ String.fromCharCode(65 + optionIndex) }}</span>
                  {{ option }}
                </label>
              </div>
              <textarea
                v-else
                v-model="answers[quiz.id]"
                rows="3"
                placeholder="写下你的理解..."
              ></textarea>
            </article>
            <button class="primary-button submit-button" :disabled="busy" @click="submitQuiz">
              {{ busy ? "AI 正在评估..." : "提交并查看评估" }}
            </button>
          </section>

          <section v-if="evaluation" class="result-card">
            <div class="score">
              <strong>{{ evaluation.evaluation?.score }}</strong><span>分</span>
            </div>
            <div>
              <p class="eyebrow">AI EVALUATION</p>
              <h3>{{ evaluation.evaluation?.score >= 85 ? "掌握得很好" : "继续巩固一下" }}</h3>
              <p>{{ evaluation.evaluation?.recommendation }}</p>
            </div>
          </section>
        </template>
      </div>

      <div v-else-if="activeView === 'tutor'" class="view tutor-view">
        <section class="chat-panel">
          <div class="chat-intro">
            <div class="ai-orb">✦</div>
            <h3>AI 学习助教</h3>
            <p>基于你的学习画像和当前任务提供针对性解答</p>
          </div>
          <div class="messages">
            <article v-for="(message, index) in messages" :key="index" :class="message.role">
              <span>{{ message.role === "assistant" ? "AI" : "我" }}</span>
              <p>{{ message.content }}</p>
            </article>
            <article v-if="busy" class="assistant typing"><span>AI</span><p>正在思考...</p></article>
          </div>
          <form class="chat-input" @submit.prevent="askTutor">
            <input v-model="tutorMessage" placeholder="输入你的问题..." />
            <button :disabled="busy || !tutorMessage.trim()">发送 ↑</button>
          </form>
        </section>
      </div>

      <div v-else class="view profile-view">
        <section class="profile-hero">
          <div class="profile-avatar">{{ session.user?.nickname?.slice(0, 1) }}</div>
          <div>
            <p class="eyebrow">LEARNER PROFILE</p>
            <h1>{{ session.user?.nickname }}</h1>
            <p>当前学习阶段：{{ profile?.level || "beginner" }}</p>
          </div>
        </section>
        <section class="profile-grid">
          <div class="panel mastery-panel">
            <div class="section-title"><h3>知识掌握度</h3><span>{{ masteryEntries.length }} 项</span></div>
            <div v-for="[name, value] in masteryEntries" :key="name" class="mastery-row">
              <div><strong>{{ name }}</strong><span>{{ Math.round(value * 100) }}%</span></div>
              <div class="progress"><i :style="{ width: `${value * 100}%` }"></i></div>
            </div>
            <p v-if="!masteryEntries.length" class="empty">完成测验后将生成掌握度数据</p>
          </div>
          <div class="panel">
            <div class="section-title"><h3>待加强知识点</h3></div>
            <div class="tags">
              <span v-for="item in profile?.weak_points || []" :key="item">{{ item }}</span>
            </div>
            <p v-if="!profile?.weak_points?.length" class="empty">当前没有明显薄弱项</p>
          </div>
          <div class="panel">
            <div class="section-title"><h3>学习偏好</h3></div>
            <div class="tags preference">
              <span v-for="item in profile?.preferences || []" :key="item">{{ item }}</span>
            </div>
            <p v-if="!profile?.preferences?.length" class="empty">继续学习以完善你的画像</p>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>
