"""Application settings and configuration."""

import os

# ВАЖНО: настройку CUDA-аллокатора нужно выполнить ДО импорта torch,
# т.к. переменная окружения читается при инициализации PyTorch.
os.environ.setdefault(
    "PYTORCH_CUDA_ALLOC_CONF",
    "garbage_collection_threshold:0.8,max_split_size_mb:64",
)

import torch  # noqa: E402
from dotenv import load_dotenv  # noqa: E402

# Load environment variables from .env file
load_dotenv()


class Settings:
    """Application settings container."""

    # FFmpeg paths (Windows-specific)
    FFMPEG_PATH = r"C:\ffmpeg\bin\ffmpeg.exe"
    FFPROBE_PATH = r"C:\ffmpeg\bin\ffprobe.exe"

    # Model settings
    WHISPER_MODEL = "large-v2"
    DEFAULT_LANGUAGE = "ru"

    # Compute settings: int8 существенно уменьшает потребление памяти модели,
    # что критично для GPU с малым объёмом VRAM (~2 ГБ).
    COMPUTE_TYPE = "int8"
    BATCH_SIZE = 4

    # Memory management
    MAX_RAM_MB = 4000           # жёсткий бюджет RAM процесса (0 = без лимита)
    MAX_VRAM_FRACTION = 0.85    # потолок доли VRAM, используемой PyTorch

    # Chunked transcription
    CHUNK_LENGTH_SEC = 90       # длина куска аудио в секундах
    CHUNK_OVERLAP_SEC = 5       # перекрытие кусков для непрерывности текста
    ENABLE_CHUNKING = True

    # Diarization
    ENABLE_DIARIZATION = True

    # Logging
    LOG_DIR = None              # None => каталог 'logs' рядом с проектом

    # Hugging Face token
    HF_TOKEN = os.environ.get("HF_TOKEN")

    # Оценочный размер весов модели (ГБ) в float16; в int8 ~половина.
    _MODEL_WEIGHTS_GB = {
        "tiny": 0.08,
        "base": 0.15,
        "small": 0.49,
        "medium": 1.52,
        "large-v1": 3.10,
        "large-v2": 3.10,
        "large-v3": 3.10,
    }
    _ACTIVATION_OVERHEAD_GB = 0.6

    @staticmethod
    def get_device() -> str:
        """Get available device (cuda or cpu)."""
        return "cuda" if torch.cuda.is_available() else "cpu"

    @staticmethod
    def get_compute_type(device: str) -> str:
        """
        Get compute type based on device.

        Для обеих платформ используется 'int8' — это даёт наибольшую экономию
        памяти, что важно для стабильной работы при ограниченном VRAM/RAM.
        """
        return "int8"

    @classmethod
    def estimate_model_vram_gb(cls, model_name: str, compute_type: str) -> float:
        """
        Оценочный объём VRAM под модель (веса + активации) в ГБ.

        Используется для авто-fallback на CPU, если свободной VRAM не хватает.
        """
        weights = cls._MODEL_WEIGHTS_GB.get(model_name, 3.10)
        if compute_type == "int8":
            weights /= 2.0
        return weights + cls._ACTIVATION_OVERHEAD_GB

    @classmethod
    def setup_environment(cls):
        """Setup environment variables for FFmpeg."""
        os.environ["FFMPEG_PATH"] = cls.FFMPEG_PATH
        os.environ["FFPROBE_PATH"] = cls.FFPROBE_PATH
