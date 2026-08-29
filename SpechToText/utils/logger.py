"""Логирование работы приложения в консоль и отдельный текстовый файл."""

import logging
import os
from datetime import datetime


LOGGER_NAME = "s2t"


def _default_log_dir():
    """Каталог логов по умолчанию: <корень проекта>/logs."""
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, "logs")


def setup_logger(log_dir=None):
    """
    Настроить общий логгер приложения.

    Создаёт логгер 's2t' с двумя обработчиками:
      - вывод в консоль;
      - запись в текстовый файл <log_dir>/whisper_YYYYMMDD_HHMMSS.txt.

    Args:
        log_dir: Каталог для файла лога. Если None — используется каталог
                 'logs' рядом с проектом.

    Returns:
        Настроенный logging.Logger.
    """
    logger = logging.getLogger(LOGGER_NAME)

    # Идемпотентность: повторная настройка не добавляет хендлеры повторно.
    if getattr(logger, "_s2t_configured", False):
        return logger

    logger.setLevel(logging.INFO)

    fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")

    # Консоль
    console = logging.StreamHandler()
    console.setFormatter(fmt)
    logger.addHandler(console)

    # Файл
    if not log_dir:
        log_dir = _default_log_dir()
    os.makedirs(log_dir, exist_ok=True)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    log_path = os.path.join(log_dir, f"whisper_{ts}.txt")
    file_handler = logging.FileHandler(log_path, encoding="utf-8")
    file_handler.setFormatter(fmt)
    logger.addHandler(file_handler)

    logger._s2t_configured = True
    logger.info(f"Лог-файл: {log_path}")
    return logger


def get_logger():
    """Вернуть общий логгер приложения (без повторной настройки)."""
    return logging.getLogger(LOGGER_NAME)