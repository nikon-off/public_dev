"""Модуль валидации входных данных для проекта av_skleyka."""

import os

from utils.logger import logger


def validate_file_exists(file_path: str) -> bool:
    """Проверяет существование файла по указанному пути.
    
    Args:
        file_path: Путь к файлу для проверки.
        
    Returns:
        True, если файл существует, иначе False.
    """
    if not os.path.isfile(file_path):
        logger.error(f'Файл не найден: {file_path}')
        return False
    return True


def validate_offset(offset: float, video_duration: float) -> bool:
    """Проверяет корректность смещения относительно длительности видео.
    
    Проверяет, что:
    - offset >= 0
    - offset < video_duration
    
    Args:
        offset: Смещение в секундах.
        video_duration: Длительность видео в секундах.
        
    Returns:
        True, если смещение корректно, иначе False.
    """
    if offset < 0:
        logger.error(f'Смещение не может быть отрицательным: {offset}')
        return False
    
    if offset >= video_duration:
        logger.error(
            f'Смещение ({offset} сек) не может быть больше или равно '
            f'длительности видео ({video_duration} сек)'
        )
        return False
    
    return True
