"""Application settings and configuration."""

import os
import torch
from dotenv import load_dotenv

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
    
    # Processing settings
    BATCH_SIZE = 16
    
    # Device settings
    @staticmethod
    def get_device() -> str:
        """Get available device (cuda or cpu)."""
        return "cuda" if torch.cuda.is_available() else "cpu"
    
    @staticmethod
    def get_compute_type(device: str) -> str:
        """Get compute type based on device."""
        return "float16" if device == "cuda" else "int8"
    
    # Hugging Face token
    HF_TOKEN = os.environ.get("HF_TOKEN")
    
    @classmethod
    def setup_environment(cls):
        """Setup environment variables for FFmpeg."""
        os.environ["FFMPEG_PATH"] = cls.FFMPEG_PATH
        os.environ["FFPROBE_PATH"] = cls.FFPROBE_PATH
