import hashlib
import json
import re
import uuid
from pathlib import Path
from typing import Any

import numpy as np
from docx import Document
from pypdf import PdfReader

from app.config import Settings


class RagService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.index_file = settings.data_dir / "chunks.json"
        self.chunks: list[dict[str, Any]] = self._load()
        self._encoder = None

    def _load(self) -> list[dict[str, Any]]:
        if not self.index_file.exists():
            return []
        return json.loads(self.index_file.read_text(encoding="utf-8"))

    def _save(self) -> None:
        self.index_file.write_text(
            json.dumps(self.chunks, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    def _extract(self, path: Path) -> str:
        suffix = path.suffix.lower()
        if suffix == ".pdf":
            return "\n".join(page.extract_text() or "" for page in PdfReader(path).pages)
        if suffix == ".docx":
            return "\n".join(p.text for p in Document(path).paragraphs)
        if suffix in {".txt", ".md"}:
            return path.read_text(encoding="utf-8")
        raise ValueError("Only PDF, DOCX, TXT and Markdown files are supported")

    def _split(self, text: str) -> list[str]:
        text = re.sub(r"\s+", " ", text).strip()
        if not text:
            return []
        result, start = [], 0
        while start < len(text):
            end = min(start + self.settings.chunk_size, len(text))
            result.append(text[start:end])
            if end == len(text):
                break
            start = end - self.settings.chunk_overlap
        return result

    def _hash_embedding(self, text: str, dimensions: int = 384) -> np.ndarray:
        vector = np.zeros(dimensions, dtype=np.float32)
        for token in re.findall(r"[\w\u4e00-\u9fff]+", text.lower()):
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            vector[int.from_bytes(digest[:4], "big") % dimensions] += 1
        norm = np.linalg.norm(vector)
        return vector / norm if norm else vector

    def _embed(self, texts: list[str]) -> np.ndarray:
        if self.settings.use_bge:
            if self._encoder is None:
                from sentence_transformers import SentenceTransformer

                self._encoder = SentenceTransformer(self.settings.embedding_model)
            return np.asarray(
                self._encoder.encode(texts, normalize_embeddings=True), dtype=np.float32
            )
        return np.vstack([self._hash_embedding(text) for text in texts])

    def ingest(self, path: Path, title: str) -> dict[str, Any]:
        document_id = str(uuid.uuid4())
        contents = self._split(self._extract(path))
        vectors = self._embed(contents) if contents else np.empty((0, 384))
        for index, (content, vector) in enumerate(zip(contents, vectors)):
            self.chunks.append(
                {
                    "document_id": document_id,
                    "chunk_id": f"{document_id}-{index}",
                    "title": title,
                    "content": content,
                    "embedding": vector.tolist(),
                }
            )
        self._save()
        return {"document_id": document_id, "title": title, "chunk_count": len(contents)}

    def search(
        self, query: str, top_k: int = 5, document_id: str | None = None
    ) -> list[dict[str, Any]]:
        candidates = [
            chunk
            for chunk in self.chunks
            if document_id is None or chunk["document_id"] == document_id
        ]
        if not candidates:
            return []
        query_vector = self._embed([query]).astype(np.float32)
        matrix = np.asarray([chunk["embedding"] for chunk in candidates], dtype=np.float32)
        try:
            import faiss

            index = faiss.IndexFlatIP(matrix.shape[1])
            index.add(matrix)
            scores, positions = index.search(query_vector, min(top_k, len(candidates)))
            ranked = [(candidates[position], float(score)) for score, position in zip(scores[0], positions[0])]
        except ImportError:
            scores = matrix @ query_vector[0]
            positions = np.argsort(scores)[::-1][:top_k]
            ranked = [(candidates[position], float(scores[position])) for position in positions]
        scored = []
        for chunk, score in ranked:
            scored.append(
                {
                    "document_id": chunk["document_id"],
                    "chunk_id": chunk["chunk_id"],
                    "title": chunk["title"],
                    "content": chunk["content"],
                    "score": round(score, 4),
                }
            )
        return scored
