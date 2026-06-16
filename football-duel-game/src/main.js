const FIELD_WIDTH = 960;
const FIELD_HEIGHT = 560;
const PLAYER_RADIUS = 22;
const BALL_RADIUS = 12;
const GOAL_HEIGHT = 148;
const GOAL_DEPTH = 28;
const PLAYER_SPEED = 285;
const PLAYER_FRICTION = 0.82;
const BALL_FRICTION = 0.987;
const KICK_POWER = 540;

const canvas = document.querySelector("#field");
const ctx = canvas.getContext("2d");
const blueScore = document.querySelector("#blueScore");
const redScore = document.querySelector("#redScore");
const statusEl = document.querySelector("#status");
const noticeEl = document.querySelector("#notice");
const roomCodeEl = document.querySelector("#roomCode");
const remoteCodeEl = document.querySelector("#remoteCode");
const buttons = {
  solo: document.querySelector("#soloBtn"),
  localHost: document.querySelector("#localHostBtn"),
  webHost: document.querySelector("#webHostBtn"),
  joinLocal: document.querySelector("#joinLocalBtn"),
  joinWeb: document.querySelector("#joinWebBtn"),
  acceptAnswer: document.querySelector("#acceptAnswerBtn"),
  copy: document.querySelector("#copyBtn"),
};

let mode = "solo";
let role = "host";
let score = { blue: 0, red: 0 };
let lastFrame = performance.now();
let resetTimer = 0;
let channel = null;
let peer = null;
let broadcast = null;
let broadcastRoom = "";
let remoteInput = emptyInput();
const pressed = new Set();

const state = {
  players: {
    blue: { x: 250, y: FIELD_HEIGHT / 2, vx: 0, vy: 0, facingX: 1, facingY: 0 },
    red: { x: FIELD_WIDTH - 250, y: FIELD_HEIGHT / 2, vx: 0, vy: 0, facingX: -1, facingY: 0 },
  },
  ball: { x: FIELD_WIDTH / 2, y: FIELD_HEIGHT / 2, vx: 0, vy: 0 },
};

function emptyInput() {
  return { up: false, down: false, left: false, right: false, kick: false };
}

function localInput() {
  return {
    up: pressed.has("KeyW") || pressed.has("ArrowUp"),
    down: pressed.has("KeyS") || pressed.has("ArrowDown"),
    left: pressed.has("KeyA") || pressed.has("ArrowLeft"),
    right: pressed.has("KeyD") || pressed.has("ArrowRight"),
    kick: pressed.has("Space"),
  };
}

function isHost() {
  return mode === "solo" || role === "host";
}

function setMode(nextMode, nextRole) {
  mode = nextMode;
  role = nextRole;
  document.querySelectorAll(".button-grid button").forEach((button) => button.classList.remove("active"));
  if (mode === "solo") buttons.solo.classList.add("active");
  if (mode === "local" && role === "host") buttons.localHost.classList.add("active");
  if (mode === "webrtc" && role === "host") buttons.webHost.classList.add("active");
}

function setNotice(text) {
  noticeEl.textContent = text;
}

function setStatus(text) {
  statusEl.textContent = text;
}

function updateScore() {
  blueScore.textContent = score.blue;
  redScore.textContent = score.red;
}

function resetPositions(scoredSide = "") {
  state.players.blue = { x: 250, y: FIELD_HEIGHT / 2, vx: 0, vy: 0, facingX: 1, facingY: 0 };
  state.players.red = { x: FIELD_WIDTH - 250, y: FIELD_HEIGHT / 2, vx: 0, vy: 0, facingX: -1, facingY: 0 };
  state.ball = {
    x: FIELD_WIDTH / 2,
    y: FIELD_HEIGHT / 2,
    vx: scoredSide === "blue" ? -180 : scoredSide === "red" ? 180 : 0,
    vy: 0,
  };
}

function startSolo() {
  closeConnections();
  setMode("solo", "host");
  score = { blue: 0, red: 0 };
  updateScore();
  resetPositions();
  setStatus("练习模式");
  setNotice("你控制蓝队，红队由电脑控制。");
}

function startLocalHost() {
  closeConnections();
  setMode("local", "host");
  broadcastRoom = Math.random().toString(36).slice(2, 7).toUpperCase();
  roomCodeEl.value = broadcastRoom;
  openBroadcast();
  setStatus(`本机房间 ${broadcastRoom}`);
  setNotice("在另一个浏览器标签页打开本页，输入房间码加入。");
}

function joinLocalRoom() {
  const code = remoteCodeEl.value.trim().toUpperCase();
  if (!code) return;
  closeConnections();
  setMode("local", "guest");
  broadcastRoom = code;
  roomCodeEl.value = code;
  openBroadcast();
  setStatus(`已加入 ${code}`);
  setNotice("你控制红队，主标签页控制蓝队。");
}

function openBroadcast() {
  broadcast = new BroadcastChannel("football-duel-game");
  broadcast.onmessage = (event) => {
    const data = event.data;
    if (data.room !== broadcastRoom || data.from === role) return;
    handleMessage(data.message);
  };
}

async function createWebHost() {
  closeConnections();
  setMode("webrtc", "host");
  peer = createPeer();
  channel = peer.createDataChannel("football");
  bindChannel();
  const offer = await peer.createOffer();
  await peer.setLocalDescription(offer);
  await waitForIce(peer);
  roomCodeEl.value = btoa(JSON.stringify(peer.localDescription));
  setStatus("等待客队");
  setNotice("把主机码发给朋友，收到客队码后粘贴并点“主机确认客队码”。");
}

async function joinWebGame() {
  const code = remoteCodeEl.value.trim();
  if (!code) return;
  closeConnections();
  setMode("webrtc", "guest");
  peer = createPeer();
  peer.ondatachannel = (event) => {
    channel = event.channel;
    bindChannel();
  };
  await peer.setRemoteDescription(JSON.parse(atob(code)));
  const answer = await peer.createAnswer();
  await peer.setLocalDescription(answer);
  await waitForIce(peer);
  roomCodeEl.value = btoa(JSON.stringify(peer.localDescription));
  setStatus("已生成客队码");
  setNotice("把客队码发回给主机，连接后你控制红队。");
}

async function acceptAnswer() {
  if (!peer || !remoteCodeEl.value.trim()) return;
  await peer.setRemoteDescription(JSON.parse(atob(remoteCodeEl.value.trim())));
  setStatus("正在连接");
}

function createPeer() {
  const pc = new RTCPeerConnection({ iceServers: [{ urls: "stun:stun.l.google.com:19302" }] });
  pc.onconnectionstatechange = () => {
    setStatus(pc.connectionState === "connected" ? "已连接" : pc.connectionState);
  };
  return pc;
}

function bindChannel() {
  channel.onopen = () => {
    setStatus("已连接");
    setNotice(role === "host" ? "你控制蓝队，朋友控制红队。" : "你控制红队，主机控制蓝队。");
  };
  channel.onclose = () => setStatus("已断开");
  channel.onmessage = (event) => handleMessage(JSON.parse(event.data));
}

function waitForIce(pc) {
  if (pc.iceGatheringState === "complete") return Promise.resolve();
  return new Promise((resolve) => {
    const timeout = window.setTimeout(resolve, 1800);
    pc.addEventListener("icegatheringstatechange", () => {
      if (pc.iceGatheringState === "complete") {
        window.clearTimeout(timeout);
        resolve();
      }
    });
  });
}

function closeConnections() {
  if (broadcast) broadcast.close();
  if (channel) channel.close();
  if (peer) peer.close();
  broadcast = null;
  channel = null;
  peer = null;
  broadcastRoom = "";
  roomCodeEl.value = "";
  remoteCodeEl.value = "";
  remoteInput = emptyInput();
}

function send(message) {
  if (channel?.readyState === "open") channel.send(JSON.stringify(message));
  if (broadcast) broadcast.postMessage({ room: broadcastRoom, from: role, message });
}

function handleMessage(message) {
  if (message.type === "input") remoteInput = message.input;
  if (message.type === "state" && !isHost()) {
    hydrate(message.state);
    score = message.score;
    updateScore();
    setNotice(message.notice);
  }
}

function snapshot() {
  return JSON.parse(JSON.stringify(state));
}

function hydrate(nextState) {
  state.players.blue = nextState.players.blue;
  state.players.red = nextState.players.red;
  state.ball = nextState.ball;
}

function cpuInput() {
  const player = state.players.red;
  return {
    up: state.ball.y < player.y - 8,
    down: state.ball.y > player.y + 8,
    left: state.ball.x < player.x - 8,
    right: state.ball.x > player.x + 8,
    kick: Math.hypot(state.ball.x - player.x, state.ball.y - player.y) < 54,
  };
}

function stepGame(dt) {
  const blueInput = localInput();
  const redInput = mode === "solo" ? cpuInput() : remoteInput;
  applyPlayerInput(state.players.blue, blueInput, dt);
  applyPlayerInput(state.players.red, redInput, dt);
  collidePlayers();
  collidePlayerBall(state.players.blue, blueInput);
  collidePlayerBall(state.players.red, redInput);
  moveBall(dt);
  detectGoal();
}

function applyPlayerInput(player, input, dt) {
  let ax = 0;
  let ay = 0;
  if (input.left) ax -= 1;
  if (input.right) ax += 1;
  if (input.up) ay -= 1;
  if (input.down) ay += 1;
  const length = Math.hypot(ax, ay) || 1;
  ax /= length;
  ay /= length;
  if (ax || ay) {
    player.facingX = ax;
    player.facingY = ay;
  }
  player.vx = player.vx * PLAYER_FRICTION + ax * PLAYER_SPEED * (1 - PLAYER_FRICTION);
  player.vy = player.vy * PLAYER_FRICTION + ay * PLAYER_SPEED * (1 - PLAYER_FRICTION);
  player.x = clamp(player.x + player.vx * dt, PLAYER_RADIUS + 6, FIELD_WIDTH - PLAYER_RADIUS - 6);
  player.y = clamp(player.y + player.vy * dt, PLAYER_RADIUS + 6, FIELD_HEIGHT - PLAYER_RADIUS - 6);
}

function collidePlayers() {
  const blue = state.players.blue;
  const red = state.players.red;
  const dx = red.x - blue.x;
  const dy = red.y - blue.y;
  const distance = Math.hypot(dx, dy);
  const minDistance = PLAYER_RADIUS * 2;
  if (distance >= minDistance || distance === 0) return;
  const nx = dx / distance;
  const ny = dy / distance;
  const push = (minDistance - distance) / 2;
  blue.x -= nx * push;
  blue.y -= ny * push;
  red.x += nx * push;
  red.y += ny * push;
}

function collidePlayerBall(player, input) {
  const dx = state.ball.x - player.x;
  const dy = state.ball.y - player.y;
  const distance = Math.hypot(dx, dy);
  const minDistance = PLAYER_RADIUS + BALL_RADIUS;
  if (distance >= minDistance || distance === 0) return;
  const nx = dx / distance;
  const ny = dy / distance;
  state.ball.x += nx * (minDistance - distance);
  state.ball.y += ny * (minDistance - distance);
  state.ball.vx += nx * 92 + player.vx * 0.55;
  state.ball.vy += ny * 92 + player.vy * 0.55;
  if (input.kick) {
    state.ball.vx += (player.facingX || nx) * KICK_POWER;
    state.ball.vy += (player.facingY || ny) * KICK_POWER;
  }
}

function moveBall(dt) {
  const ball = state.ball;
  ball.x += ball.vx * dt;
  ball.y += ball.vy * dt;
  ball.vx *= BALL_FRICTION;
  ball.vy *= BALL_FRICTION;
  const goalTop = FIELD_HEIGHT / 2 - GOAL_HEIGHT / 2;
  const goalBottom = FIELD_HEIGHT / 2 + GOAL_HEIGHT / 2;
  const inGoalMouth = ball.y > goalTop && ball.y < goalBottom;
  if (ball.y < BALL_RADIUS || ball.y > FIELD_HEIGHT - BALL_RADIUS) {
    ball.y = clamp(ball.y, BALL_RADIUS, FIELD_HEIGHT - BALL_RADIUS);
    ball.vy *= -0.78;
  }
  if (!inGoalMouth && (ball.x < BALL_RADIUS || ball.x > FIELD_WIDTH - BALL_RADIUS)) {
    ball.x = clamp(ball.x, BALL_RADIUS, FIELD_WIDTH - BALL_RADIUS);
    ball.vx *= -0.78;
  }
}

function detectGoal() {
  if (resetTimer) return;
  const goalTop = FIELD_HEIGHT / 2 - GOAL_HEIGHT / 2;
  const goalBottom = FIELD_HEIGHT / 2 + GOAL_HEIGHT / 2;
  if (state.ball.y < goalTop || state.ball.y > goalBottom) return;
  if (state.ball.x < -GOAL_DEPTH) scoreGoal("red");
  if (state.ball.x > FIELD_WIDTH + GOAL_DEPTH) scoreGoal("blue");
}

function scoreGoal(side) {
  score[side] += 1;
  updateScore();
  setNotice(`${side === "blue" ? "蓝队" : "红队"}进球，即将重新开球。`);
  resetTimer = window.setTimeout(() => {
    resetTimer = 0;
    resetPositions(side);
  }, 900);
}

function draw() {
  ctx.clearRect(0, 0, FIELD_WIDTH, FIELD_HEIGHT);
  drawField();
  drawPlayer(state.players.blue, "#2563eb", "蓝");
  drawPlayer(state.players.red, "#dc2626", "红");
  drawBall();
}

function drawField() {
  for (let x = 0; x < FIELD_WIDTH; x += 120) {
    ctx.fillStyle = x / 120 % 2 === 0 ? "#1d7c4e" : "#166b42";
    ctx.fillRect(x, 0, 120, FIELD_HEIGHT);
  }
  ctx.strokeStyle = "rgba(255,255,255,.86)";
  ctx.lineWidth = 4;
  ctx.strokeRect(24, 24, FIELD_WIDTH - 48, FIELD_HEIGHT - 48);
  ctx.beginPath();
  ctx.moveTo(FIELD_WIDTH / 2, 24);
  ctx.lineTo(FIELD_WIDTH / 2, FIELD_HEIGHT - 24);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(FIELD_WIDTH / 2, FIELD_HEIGHT / 2, 82, 0, Math.PI * 2);
  ctx.stroke();
  ctx.strokeRect(24, FIELD_HEIGHT / 2 - 112, 120, 224);
  ctx.strokeRect(FIELD_WIDTH - 144, FIELD_HEIGHT / 2 - 112, 120, 224);
  ctx.fillStyle = "rgba(255,255,255,.18)";
  ctx.fillRect(-GOAL_DEPTH, FIELD_HEIGHT / 2 - GOAL_HEIGHT / 2, GOAL_DEPTH + 24, GOAL_HEIGHT);
  ctx.fillRect(FIELD_WIDTH - 24, FIELD_HEIGHT / 2 - GOAL_HEIGHT / 2, GOAL_DEPTH + 24, GOAL_HEIGHT);
}

function drawPlayer(player, color, label) {
  ctx.save();
  ctx.translate(player.x, player.y);
  ctx.fillStyle = "rgba(0,0,0,.22)";
  ctx.beginPath();
  ctx.ellipse(0, PLAYER_RADIUS + 5, PLAYER_RADIUS * 0.82, 7, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.arc(0, 0, PLAYER_RADIUS, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "white";
  ctx.font = "700 18px Microsoft YaHei";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(label, 0, 1);
  ctx.restore();
}

function drawBall() {
  ctx.save();
  ctx.translate(state.ball.x, state.ball.y);
  ctx.fillStyle = "rgba(0,0,0,.22)";
  ctx.beginPath();
  ctx.ellipse(3, BALL_RADIUS + 6, BALL_RADIUS, 5, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#f8fafc";
  ctx.beginPath();
  ctx.arc(0, 0, BALL_RADIUS, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "#1f2937";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(-5, -8);
  ctx.lineTo(5, 8);
  ctx.moveTo(7, -6);
  ctx.lineTo(-8, 5);
  ctx.stroke();
  ctx.restore();
}

function loop(now) {
  const dt = Math.min((now - lastFrame) / 1000, 0.04);
  lastFrame = now;
  if (isHost()) {
    stepGame(dt);
    if (mode !== "solo") {
      send({ type: "state", state: snapshot(), score, notice: noticeEl.textContent });
    }
  } else {
    send({ type: "input", input: localInput() });
  }
  draw();
  requestAnimationFrame(loop);
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

window.addEventListener("keydown", (event) => {
  if (["Space", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"].includes(event.code)) event.preventDefault();
  pressed.add(event.code);
});

window.addEventListener("keyup", (event) => {
  pressed.delete(event.code);
});

buttons.solo.addEventListener("click", startSolo);
buttons.localHost.addEventListener("click", startLocalHost);
buttons.webHost.addEventListener("click", createWebHost);
buttons.joinLocal.addEventListener("click", joinLocalRoom);
buttons.joinWeb.addEventListener("click", joinWebGame);
buttons.acceptAnswer.addEventListener("click", acceptAnswer);
buttons.copy.addEventListener("click", async () => {
  if (!roomCodeEl.value) return;
  await navigator.clipboard.writeText(roomCodeEl.value);
  buttons.copy.textContent = "已复制";
  window.setTimeout(() => { buttons.copy.textContent = "复制代码"; }, 1200);
});

startSolo();
requestAnimationFrame(loop);
