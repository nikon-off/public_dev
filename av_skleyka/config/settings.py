"""Configuration settings for AudioVideoSyncer."""

import os.path

# FFmpeg binary paths (Windows format)
FFMPEG_PATH = "C:\\ffmpeg\\bin\\ffmpeg.exe"
FFPROBE_PATH = "C:\\ffmpeg\\bin\\ffprobe.exe"

# Maximum RAM usage in GB
MAX_RAM_GB = 3


def check_ffmpeg_binaries() -> bool:
    """
    Check if FFmpeg and FFprobe binaries exist at the configured paths.

    Returns:
        True if both FFMPEG_PATH and FFPROBE_PATH files exist, False otherwise.
    """
    ffmpeg_exists = os.path.exists(FFMPEG_PATH)
    ffprobe_exists = os.path.exists(FFPROBE_PATH)
    return ffmpeg_exists and ffprobe_exists
