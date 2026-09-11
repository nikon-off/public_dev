"""Модуль мониторинга потребления оперативной памяти."""

import os

import psutil

from config.settings import MAX_RAM_GB
from utils.logger import logger


class MemoryMonitor:
    """Класс для отслеживания использования оперативной памяти текущим процессом."""

    def __init__(self):
        """Инициализирует монитор памяти.
        
        Переводит MAX_RAM_GB в байты и устанавливает порог предупреждения (80% от лимита).
        """
        self.limit_bytes = MAX_RAM_GB * 1024 * 1024 * 1024  # Перевод ГБ в байты
        self.warning_threshold = self.limit_bytes * 0.8  # 80% от лимита

    def get_current_usage_mb(self) -> float:
        """Возвращает текущее потребление памяти процессом Python в мегабайтах.
        
        Returns:
            float: Текущее использование памяти в МБ.
        """
        process = psutil.Process(os.getpid())
        memory_info = process.memory_info().rss
        return memory_info / (1024 * 1024)  # Перевод байт в МБ

    def check_limit(self) -> bool:
        """Проверяет, не превышен ли лимит потребления памяти.
        
        Returns:
            bool: True, если лимит не превышен, False в противном случае.
        """
        current_usage_bytes = self.get_current_usage_mb() * 1024 * 1024
        
        if current_usage_bytes > self.limit_bytes:
            logger.critical("Превышен лимит памяти!")
            return False
        
        if current_usage_bytes > self.warning_threshold:
            logger.warning(f"Потребление памяти ({current_usage_bytes / (1024 * 1024):.2f} МБ) превышает порог предупреждения")
        
        return True
