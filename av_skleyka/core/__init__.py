"""Пакет core для модулей обработки медиа."""

from .media_processor import get_duration, build_ffmpeg_command

__all__ = ['get_duration', 'build_ffmpeg_command']
