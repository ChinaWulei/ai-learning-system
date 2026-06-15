<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { api } from "../api";

const props = defineProps({
  text: { type: String, default: "" },
  thinking: { type: Boolean, default: false },
});

const browserSpeechSupported = "speechSynthesis" in window && "SpeechSynthesisUtterance" in window;
const speaking = ref(false);
const paused = ref(false);
const loadingVoice = ref(false);
const rate = ref(1);
const subtitle = ref("你好，我是林老师。今天想学习什么？");
const segmentIndex = ref(0);
const segments = ref([]);
const audioProgress = ref(0);
const speechSource = ref("Gemini");
const speechError = ref("");
let currentSession = 0;
let selectedVoice = null;
let audio = null;
let audioUrl = "";

const statusText = computed(() => {
  if (props.thinking) return "正在备课";
  if (loadingVoice.value) return "正在生成语音";
  if (speaking.value && paused.value) return "讲解已暂停";
  if (speaking.value) return "正在讲解";
  return "在线答疑";
});

const progress = computed(() => {
  if (audio) return audioProgress.value;
  if (!segments.value.length) return 0;
  return Math.round(((segmentIndex.value + (speaking.value ? 1 : 0)) / segments.value.length) * 100);
});

function selectVoice() {
  const voices = window.speechSynthesis?.getVoices() || [];
  selectedVoice = voices.find((voice) => /zh-CN/i.test(voice.lang) && /Xiaoxiao|晓晓|女|Female/i.test(voice.name))
    || voices.find((voice) => /zh-CN/i.test(voice.lang))
    || voices.find((voice) => /^zh/i.test(voice.lang))
    || null;
}

function splitSpeech(text) {
  const normalized = text
    .replace(/```[\s\S]*?```/g, "代码示例")
    .replace(/[*#>`_~]/g, "")
    .replace(/\s+/g, " ")
    .trim();
  return normalized.match(/[^。！？；.!?;]+[。！？；.!?;]?/g)?.filter(Boolean) || [];
}

function stop() {
  currentSession += 1;
  if (audio) {
    audio.pause();
    audio.removeAttribute("src");
    audio.load();
    audio = null;
  }
  if (audioUrl) {
    URL.revokeObjectURL(audioUrl);
    audioUrl = "";
  }
  window.speechSynthesis?.cancel();
  speaking.value = false;
  paused.value = false;
  loadingVoice.value = false;
  segmentIndex.value = 0;
  audioProgress.value = 0;
}

function speakBrowserSegment(session) {
  if (session !== currentSession || segmentIndex.value >= segments.value.length) {
    speaking.value = false;
    paused.value = false;
    return;
  }
  const text = segments.value[segmentIndex.value];
  subtitle.value = text;
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "zh-CN";
  utterance.rate = rate.value;
  utterance.pitch = 1.04;
  if (selectedVoice) utterance.voice = selectedVoice;
  utterance.onend = () => {
    if (session !== currentSession) return;
    segmentIndex.value += 1;
    speakBrowserSegment(session);
  };
  utterance.onerror = (event) => {
    if (event.error === "canceled" || event.error === "interrupted") return;
    speaking.value = false;
  };
  window.speechSynthesis.speak(utterance);
}

function speakWithBrowser(session) {
  if (!browserSpeechSupported || session !== currentSession) {
    speaking.value = false;
    return;
  }
  selectVoice();
  speechSource.value = "系统语音";
  segmentIndex.value = 0;
  speaking.value = true;
  speakBrowserSegment(session);
}

function configureAudio(blob, session) {
  audioUrl = URL.createObjectURL(blob);
  audio = new Audio(audioUrl);
  audio.playbackRate = rate.value;
  audio.onplay = () => {
    if (session !== currentSession) return;
    speaking.value = true;
    paused.value = false;
  };
  audio.onpause = () => {
    if (session !== currentSession || audio?.ended) return;
    paused.value = true;
  };
  audio.ontimeupdate = () => {
    if (!audio?.duration || session !== currentSession) return;
    const ratio = Math.min(audio.currentTime / audio.duration, 1);
    audioProgress.value = Math.round(ratio * 100);
    segmentIndex.value = Math.min(
      Math.floor(ratio * segments.value.length),
      Math.max(segments.value.length - 1, 0),
    );
    subtitle.value = segments.value[segmentIndex.value] || subtitle.value;
  };
  audio.onended = () => {
    if (session !== currentSession) return;
    speaking.value = false;
    paused.value = false;
    audioProgress.value = 100;
  };
}

async function speak(text = props.text) {
  if (!text.trim()) return;
  stop();
  segments.value = splitSpeech(text);
  if (!segments.value.length) return;
  subtitle.value = segments.value[0];
  loadingVoice.value = true;
  speechError.value = "";
  speechSource.value = "Gemini";
  const session = currentSession;
  try {
    const blob = await api.speech(text.slice(0, 6000));
    if (session !== currentSession) return;
    configureAudio(blob, session);
    loadingVoice.value = false;
    try {
      await audio.play();
    } catch (error) {
      if (error.name !== "NotAllowedError") throw error;
      speaking.value = true;
      paused.value = true;
      speechError.value = "语音已生成，请点击播放";
    }
  } catch (error) {
    if (session !== currentSession) return;
    loadingVoice.value = false;
    speechError.value = `${error.message}，已切换系统语音`;
    speakWithBrowser(session);
  }
}

function togglePause() {
  if (loadingVoice.value) return;
  if (audio) {
    if (audio.paused) {
      audio.play();
    } else {
      audio.pause();
    }
    return;
  }
  if (!speaking.value) {
    speak();
    return;
  }
  if (paused.value) {
    window.speechSynthesis.resume();
    paused.value = false;
  } else {
    window.speechSynthesis.pause();
    paused.value = true;
  }
}

function replay() {
  if (audio) {
    audio.currentTime = 0;
    audio.play();
    return;
  }
  speak();
}

function changeRate() {
  if (audio) {
    audio.playbackRate = rate.value;
  } else if (speaking.value) {
    speak();
  }
}

watch(() => props.text, (text, previous) => {
  if (text && text !== previous) speak(text);
});

onBeforeUnmount(() => {
  stop();
});
</script>

<template>
  <section class="teacher-stage" :class="{ speaking, thinking: thinking || loadingVoice }">
    <header>
      <div>
        <p class="eyebrow">DIGITAL TUTOR</p>
        <h3>林老师</h3>
      </div>
      <div class="teacher-meta">
        <small>{{ speechSource }}</small>
        <span class="teacher-status"><i></i>{{ statusText }}</span>
      </div>
    </header>

    <div class="teacher-visual">
      <div class="board-line line-one"></div>
      <div class="board-line line-two"></div>
      <div class="board-formula">f(x) = learn · practice</div>
      <img src="/images/digital-teacher.png" alt="数字教师林老师" />
      <div class="voice-wave" aria-hidden="true">
        <i v-for="index in 5" :key="index"></i>
      </div>
    </div>

    <div class="teacher-caption">
      <p>{{ props.thinking ? "我正在整理知识点，请稍等片刻。" : subtitle }}</p>
      <div class="speech-progress"><i :style="{ width: `${progress}%` }"></i></div>
    </div>

    <div class="teacher-controls">
      <button
        type="button"
        :title="paused ? '继续讲解' : speaking ? '暂停讲解' : '开始讲解'"
        :disabled="loadingVoice || !props.text"
        @click="togglePause"
      >{{ paused || !speaking ? "▶" : "Ⅱ" }}</button>
      <button
        type="button"
        title="重新讲解"
        :disabled="loadingVoice || !props.text"
        @click="replay"
      >↻</button>
      <label>
        <span>语速</span>
        <select v-model.number="rate" @change="changeRate">
          <option :value="0.8">慢</option>
          <option :value="1">标准</option>
          <option :value="1.2">快</option>
        </select>
      </label>
    </div>
    <p v-if="speechError" class="speech-warning">{{ speechError }}</p>
  </section>
</template>

<style scoped>
.teacher-stage {
  min-width: 0;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(260px, 1fr) auto auto;
  overflow: hidden;
  border: 1px solid #bdd0c6;
  border-radius: 8px;
  color: #eef6f1;
  background: #173e32;
}
.teacher-stage header {
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 22px 8px;
}
.teacher-stage h3 { margin: 0; color: white; font-size: 20px; }
.teacher-stage .eyebrow { color: #d6b477; }
.teacher-meta { display: grid; justify-items: end; gap: 6px; }
.teacher-meta small { color: #e0b975; font-size: 10px; }
.teacher-status { display: flex; align-items: center; gap: 7px; font-size: 11px; color: #c8d8d0; }
.teacher-status i { width: 7px; height: 7px; border-radius: 50%; background: #75c48e; }
.thinking .teacher-status i { background: #e0b95f; animation: status-pulse 1s infinite; }
.teacher-visual { position: relative; min-height: 0; overflow: hidden; }
.teacher-visual::after { content: ""; position: absolute; inset: auto 0 0; height: 24%; background: linear-gradient(transparent, #173e32); }
.teacher-visual img {
  position: absolute;
  z-index: 2;
  width: min(118%, 620px);
  max-width: none;
  right: -8%;
  bottom: -12%;
  transform-origin: 62% 68%;
  filter: drop-shadow(0 16px 22px #071d1666);
}
.speaking .teacher-visual img { animation: teacher-talk .52s ease-in-out infinite alternate; }
.thinking .teacher-visual img { animation: teacher-think 2.4s ease-in-out infinite; }
.board-line { position: absolute; height: 1px; background: #ffffff18; transform: rotate(-8deg); }
.line-one { width: 42%; top: 26%; left: 4%; }
.line-two { width: 30%; top: 36%; left: 8%; }
.board-formula { position: absolute; top: 15%; left: 7%; color: #b8d1c4; font-family: Georgia, serif; font-size: 13px; opacity: .7; }
.voice-wave { position: absolute; z-index: 4; right: 22px; bottom: 34px; height: 25px; display: flex; align-items: center; gap: 3px; opacity: 0; }
.speaking .voice-wave { opacity: 1; }
.voice-wave i { width: 3px; height: 7px; border-radius: 3px; background: #f0c87e; animation: wave .65s ease-in-out infinite alternate; }
.voice-wave i:nth-child(2), .voice-wave i:nth-child(4) { animation-delay: .18s; }
.voice-wave i:nth-child(3) { animation-delay: .34s; }
.teacher-caption { z-index: 4; min-height: 92px; padding: 14px 20px 12px; background: #102f27; }
.teacher-caption p { min-height: 42px; margin: 0 0 10px; color: #f7faf8; font-size: 13px; line-height: 1.65; }
.speech-progress { height: 3px; overflow: hidden; background: #ffffff1c; }
.speech-progress i { display: block; height: 100%; background: #e0b975; transition: width .25s; }
.teacher-controls { z-index: 4; display: flex; align-items: center; gap: 8px; padding: 10px 16px 15px; background: #102f27; }
.teacher-controls button {
  width: 34px;
  height: 34px;
  border: 1px solid #ffffff2e;
  border-radius: 50%;
  color: white;
  background: #ffffff0d;
}
.teacher-controls button:disabled { cursor: not-allowed; opacity: .35; }
.teacher-controls label { display: flex; align-items: center; gap: 7px; margin-left: auto; color: #a9bdb3; font-size: 11px; }
.teacher-controls select { width: auto; border: 1px solid #ffffff24; border-radius: 5px; padding: 5px 22px 5px 7px; color: white; background: #173e32; font-size: 11px; }
.speech-warning { margin: 0; padding: 0 16px 12px; color: #f1c2b5; background: #102f27; font-size: 11px; }
@keyframes teacher-talk {
  from { transform: translateY(0) rotate(-.25deg) scaleY(1); }
  to { transform: translateY(-2px) rotate(.25deg) scaleY(1.004); }
}
@keyframes teacher-think {
  0%, 100% { transform: rotate(0); }
  50% { transform: rotate(-1deg); }
}
@keyframes wave { from { height: 6px; } to { height: 24px; } }
@keyframes status-pulse { 50% { opacity: .35; } }

@media (max-width: 900px) {
  .teacher-stage { height: 520px; }
  .teacher-visual img { width: min(88%, 560px); right: 2%; }
}

@media (max-width: 600px) {
  .teacher-stage { height: 460px; grid-template-rows: auto minmax(220px, 1fr) auto auto; }
  .teacher-visual img { width: min(108%, 500px); right: -10%; }
}
</style>
