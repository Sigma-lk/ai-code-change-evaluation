import os
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer


MODEL_ID = os.getenv("MODEL_ID", "BAAI/bge-m3")
model = None


class EmbeddingRequest(BaseModel):
    model: str | None = None
    input: str | list[str]
    encoding_format: str | None = "float"


@asynccontextmanager
async def lifespan(_: FastAPI):
    global model
    # 启动阶段预加载模型，确保 /health 仅在模型可用时返回成功。
    model = SentenceTransformer(MODEL_ID, trust_remote_code=True, device="cpu")
    yield


app = FastAPI(title="Local BGE-M3 Embedding Service", lifespan=lifespan)


@app.get("/health")
def health() -> dict:
    if model is None:
        raise HTTPException(status_code=503, detail="model_not_ready")
    return {"status": "ok", "model": MODEL_ID}


@app.post("/v1/embeddings")
def embeddings(request: EmbeddingRequest) -> dict:
    if model is None:
        raise HTTPException(status_code=503, detail="model_not_ready")

    inputs = request.input if isinstance(request.input, list) else [request.input]
    if not inputs:
        raise HTTPException(status_code=400, detail="input_must_not_be_empty")

    vectors = model.encode(
        inputs,
        normalize_embeddings=True,
        convert_to_numpy=True,
        show_progress_bar=False,
    )

    data = [
        {
            "object": "embedding",
            "index": index,
            "embedding": vector.tolist(),
        }
        for index, vector in enumerate(vectors)
    ]
    total_tokens = sum(len(text.split()) for text in inputs)
    now = int(time.time())
    return {
        "object": "list",
        "data": data,
        "model": request.model or MODEL_ID,
        "created": now,
        "usage": {
            "prompt_tokens": total_tokens,
            "total_tokens": total_tokens,
        },
    }
