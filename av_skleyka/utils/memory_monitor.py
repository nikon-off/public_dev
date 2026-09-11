"""Модуль мониторинга потребления оперативной памяти.

Предоставляет класс MemoryMonitor для отслеживания использования RAM текущим процессом Python.
Используется для контроля соблюдения ограничений на потребление памяти (2-3 ГБ согласно ТЗ).
"""

import os

import psutil

from config.settings import MAX_RAM_GB
from utils.logger import logger


class MemoryMonitor:
    """Класс для отслеживания использования оперативной памяти текущим процессом.

    Предназначен для мониторинга потребления RAM процессом Python и сравнения
    с установленными лимитами. При превышении пороговых значений логирует
    предупреждения или критические ошибки.

    Attributes:
        limit_bytes: Максимально допустимое потребление памяти в байтах.
        warning_threshold: Порог предупреждения (80% от limit_bytes) в байтах.

    Example:
        >>> monitor = MemoryMonitor()
        >>> usage_mb = monitor.get_current_usage_mb()
        >>> if monitor.check_limit():
        ...     # Продолжаем работу
        ...     pass
    """

    def __init__(self):
        """Инициализирует монитор памяти.

        Переводит MAX_RAM_GB в байты и устанавливает порог предупреждения (80% от лимита).
        """
        self.limit_bytes = MAX_RAM_GB * 1024 * 1024 * 1024  # Перевод ГБ в байты
        self.warning_threshold = self.limit_bytes * 0.8  # 80% от лимита

    def _get_current_usage_bytes(self) -> int:
        """Возвращает текущее потребление памяти процессом в байтах.

        Returns:
            int: Текущее использование памяти в байтах.

        Raises:
            RuntimeError: Если не удалось получить информацию о памяти.
        """
        try:
            process = psutil.Process(os.getpid())
            return process.memory_info().rss
        except Exception as e:
            logger.error(f"Не удалось получить информацию о потреблении памяти: {e}")
            raise RuntimeError(f"Ошибка получения данных о памяти: {e}") from e

    def get_current_usage_mb(self) -> float:
        """Возвращает текущее потребление памяти процессом Python в мегабайтах.

        Returns:
            float: Текущее использование памяти в МБ.

        Raises:
            RuntimeError: Если не удалось получить информацию о памяти.
        """
        return self._get_current_usage_bytes() / (1024 * 1024)

    def check_limit(self) -> bool:
        """Проверяет, не превышен ли лимит потребления памяти.

        Сравнивает текущее потребление памяти с установленными лимитами:
        - При превышении MAX_RAM_GB логирует критическую ошибку и возвращает False
        - При превышении 80% от лимита логирует предупреждение
        - В остальных случаях возвращает True

        Returns:
            bool: True, если лимит не превышен, False в противном случае.

        Raises:
            RuntimeError: Если не удалось получить информацию о памяти.
        """
        current_usage_bytes = self._get_current_usage_bytes()

        if current_usage_bytes > self.limit_bytes:
            logger.critical("Превышен лимит памяти!")
            return False

        if current_usage_bytes > self.warning_threshold:
            logger.warning(
                f"Потребление памяти ({current_usage_bytes / (1024 * 1024):.2f} МБ) "
                f"превышает порог предупреждения ({self.warning_threshold / (1024 * 1024):.2f} МБ)"
            )

        return True
