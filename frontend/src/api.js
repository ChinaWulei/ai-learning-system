const TOKEN_KEY = "ai-learning-token";
const USER_KEY = "ai-learning-user";

export function getSession() {
  const token = localStorage.getItem(TOKEN_KEY);
  const rawUser = localStorage.getItem(USER_KEY);
  return {
    token,
    user: rawUser ? JSON.parse(rawUser) : null,
  };
}

export function saveSession(data) {
  localStorage.setItem(TOKEN_KEY, data.token);
  localStorage.setItem(
    USER_KEY,
    JSON.stringify({ userId: data.userId, nickname: data.nickname }),
  );
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request(path, options = {}) {
  const token = localStorage.getItem(TOKEN_KEY);
  const response = await fetch(path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    // Authentication failures may come from an older backend with an empty body.
  }

  if (response.status === 401 || response.status === 403) {
    clearSession();
    window.dispatchEvent(new Event("session-expired"));
    throw new Error(payload?.message || "登录状态已失效，请重新登录");
  }
  if (!payload) {
    throw new Error(`服务返回异常（HTTP ${response.status}）`);
  }
  if (!response.ok || payload.success === false) {
    throw new Error(payload.message || `请求失败（HTTP ${response.status}）`);
  }
  return payload.data;
}

async function requestAudio(path, body) {
  const token = localStorage.getItem(TOKEN_KEY);
  const response = await fetch(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  if (response.status === 401 || response.status === 403) {
    clearSession();
    window.dispatchEvent(new Event("session-expired"));
    throw new Error("登录状态已失效，请重新登录");
  }
  if (!response.ok) {
    let message = `语音生成失败（HTTP ${response.status}）`;
    try {
      const payload = await response.json();
      message = payload.message || payload.detail || message;
    } catch {
      // Keep the HTTP status message for non-JSON responses.
    }
    throw new Error(message);
  }
  return response.blob();
}

export const api = {
  login: (username, password) =>
    request("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
  enrollFace: (username, password, embedding) =>
    request("/api/auth/face/enroll", {
      method: "POST",
      body: JSON.stringify({ username, password, embedding }),
    }),
  faceLogin: (username, embedding) =>
    request("/api/auth/face/login", {
      method: "POST",
      body: JSON.stringify({ username, embedding }),
    }),
  profile: () => request("/api/users/me/profile"),
  createTask: (topic, goal) =>
    request("/api/learning/tasks", {
      method: "POST",
      body: JSON.stringify({ topic, goal }),
    }),
  submitAnswers: (taskId, answers) =>
    request(`/api/learning/tasks/${taskId}/answers`, {
      method: "POST",
      body: JSON.stringify({ answers }),
    }),
  tutor: (message, taskId) =>
    request("/api/tutor/chat", {
      method: "POST",
      body: JSON.stringify({ message, taskId: taskId || null }),
    }),
  speech: (text) => requestAudio("/api/tutor/speech", { text }),
};
