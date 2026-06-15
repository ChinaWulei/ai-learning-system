const humanConfig = {
  modelBasePath: "/models",
  cacheSensitivity: 0,
  filter: { enabled: true, equalization: true },
  face: {
    enabled: true,
    detector: { rotation: false, return: false, mask: false },
    mesh: { enabled: false },
    description: { enabled: true },
    iris: { enabled: false },
    emotion: { enabled: false },
    antispoof: { enabled: true },
    liveness: { enabled: true },
  },
  body: { enabled: false },
  hand: { enabled: false },
  object: { enabled: false },
  gesture: { enabled: false },
};

let modelPromise;
let human;

function loadModels() {
  if (!modelPromise) {
    modelPromise = import("@vladmandic/human").then(async ({ default: Human }) => {
      human = new Human(humanConfig);
      await human.load();
      await human.warmup();
    });
  }
  return modelPromise;
}

function averageEmbeddings(embeddings) {
  return embeddings[0].map((_, index) => (
    embeddings.reduce((sum, embedding) => sum + embedding[index], 0) / embeddings.length
  ));
}

const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

export async function openCamera(video) {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error("当前浏览器不支持摄像头访问");
  }
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      facingMode: "user",
      width: { ideal: 640 },
      height: { ideal: 480 },
    },
  });
  video.srcObject = stream;
  await new Promise((resolve) => {
    video.onloadeddata = resolve;
  });
  await video.play();
  return stream;
}

export function closeCamera(stream) {
  stream?.getTracks().forEach((track) => track.stop());
}

export async function captureFaceEmbedding(video, onStatus = () => {}) {
  onStatus("正在加载人脸识别模型...");
  await loadModels();

  const embeddings = [];
  const deadline = Date.now() + 20000;
  while (Date.now() < deadline && embeddings.length < 3) {
    onStatus(`请正对摄像头，保持光线充足 (${embeddings.length}/3)`);
    const result = await human.detect(video);
    if (result.face.length !== 1) {
      onStatus(result.face.length > 1 ? "画面中只能有一张人脸" : "未检测到人脸，请靠近摄像头");
      await wait(250);
      continue;
    }

    const face = result.face[0];
    const faceSize = Math.min(face.box[2], face.box[3]);
    const videoShortSide = Math.min(video.videoWidth || 640, video.videoHeight || 480);
    const minimumFaceSize = Math.max(90, Math.min(130, videoShortSide * 0.22));
    const detectionScore = Math.max(face.faceScore || 0, face.boxScore || 0);
    if (detectionScore < 0.55) {
      onStatus("画面清晰度不足，请保持稳定并改善光线");
    } else if (faceSize < minimumFaceSize) {
      onStatus("请让面部位于取景框中央");
    } else if ((face.real ?? 0) < 0.55 || (face.live ?? 0) < 0.55) {
      onStatus("活体检测中，请轻微转动头部");
    } else if (face.embedding?.length) {
      embeddings.push([...face.embedding]);
    }
    await wait(300);
  }

  if (embeddings.length < 3) {
    throw new Error("人脸采集超时，请调整光线后重试");
  }
  onStatus("人脸采集完成");
  return averageEmbeddings(embeddings);
}
