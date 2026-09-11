"""Модуль утилит для работы с файлами в проекте av_skleyka."""

import os


def generate_output_path(video_path: str) -> str:
    """Генерирует путь для выходного файла с суффиксом '_synced'.
    
    Добавляет суффикс '_synced' перед расширением файла.
    Например: 'C:\\Users\\Doc\\movie.mp4' -> 'C:\\Users\\Doc\\movie_synced.mp4'
    
    Args:
        video_path: Путь к исходному видеофайлу.
        
    Returns:
        Путь к выходному файлу с суффиксом '_synced'.
    """
    directory = os.path.dirname(video_path)
    filename = os.path.basename(video_path)
    name, ext = os.path.splitext(filename)
    
    new_filename = f'{name}_synced{ext}'
    
    if directory:
        return os.path.join(directory, new_filename)
    return new_filename


def check_output_conflict(output_path: str) -> bool:
    """Проверяет, существует ли уже файл с указанным именем.
    
    Args:
        output_path: Путь к файлу для проверки.
        
    Returns:
        True, если файл существует (конфликт), иначе False.
    """
    return os.path.isfile(output_path)
