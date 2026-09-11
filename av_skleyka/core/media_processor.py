"""Модуль низкоуровневого взаимодействия с FFmpeg/FFprobe."""

import subprocess
from typing import List

from config.settings import FFMPEG_PATH, FFPROBE_PATH
from utils.logger import logger


def get_duration(file_path: str) -> float:
    """Получает длительность медиафайла в секундах через ffprobe.

    Args:
        file_path: Путь к медиафайлу.

    Returns:
        Длительность файла в секундах (float).

    Raises:
        RuntimeError: При ошибке запуска ffprobe или парсинга вывода.
    """
    cmd = [
        FFPROBE_PATH,
        '-v', 'error',
        '-show_entries', 'format=duration',
        '-of', 'default=noprint_wrappers=1:nokey=1',
        file_path
    ]

    logger.debug(f'Запуск ffprobe для получения длительности: {file_path}')

    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            check=False
        )

        if result.returncode != 0:
            error_msg = f'Ошибка ffprobe: {result.stderr.strip()}'
            logger.error(error_msg)
            raise RuntimeError(error_msg)

        duration_str = result.stdout.strip()
        if not duration_str:
            error_msg = 'ffprobe вернул пустой вывод'
            logger.error(error_msg)
            raise RuntimeError(error_msg)

        try:
            duration = float(duration_str)
        except ValueError as e:
            error_msg = f'Не удалось распарсить длительность "{duration_str}": {e}'
            logger.error(error_msg)
            raise RuntimeError(error_msg)

        logger.debug(f'Длительность файла {file_path}: {duration} сек.')
        return duration

    except FileNotFoundError as e:
        error_msg = f'Не найден исполняемый файл ffprobe: {e}'
        logger.error(error_msg)
        raise RuntimeError(error_msg)
    except Exception as e:
        error_msg = f'Неожиданная ошибка при получении длительности: {e}'
        logger.error(error_msg)
        raise RuntimeError(error_msg)


def build_ffmpeg_command(
    video_path: str,
    audio_path: str,
    offset: float,
    output_path: str
) -> List[str]:
    """Собирает список аргументов для запуска ffmpeg.

    Формирует команду для склейки видео и аудио с применением смещения
    по времени для видеопотока.

    Args:
        video_path: Путь к входному видеофайлу.
        audio_path: Путь к входному аудиофайлу.
        offset: Смещение по времени в секундах (применяется к видео).
        output_path: Путь к выходному файлу.

    Returns:
        Список строк-аргументов для передачи в subprocess.Popen.
    """
    cmd = [
        FFMPEG_PATH,
        '-y',                          # Перезапись выходного файла без вопросов
        '-progress', 'pipe:1',         # Вывод прогресса в stdout
        '-itsoffset', str(offset),     # Смещение применяется перед видео
        '-i', video_path,              # Входной видеофайл
        '-i', audio_path,              # Входной аудиофайл
        '-map', '0:v:0',               # Берём видео из первого входа
        '-map', '1:a:0',               # Берём аудио из второго входа
        '-c:v', 'copy',                # Копирование видеопотока без перекодирования
        '-c:a', 'copy',                # Копирование аудиопотока без перекодирования
        output_path                    # Выходной файл
    ]

    logger.info(f'Сформирована команда ffmpeg для склейки: видео={video_path}, аудио={audio_path}, смещение={offset}сек')
    logger.debug(f'Команда: {" ".join(cmd)}')

    return cmd
