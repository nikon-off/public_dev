"""Модуль отслеживания прогресса обработки медиафайлов.

Использует tqdm для визуализации прогресса и парсит вывод ffmpeg
для обновления прогресс-бара в реальном времени.
"""

import re
from tqdm import tqdm
from utils.logger import logger


class ProgressTracker:
    """Класс для отслеживания прогресса обработки видео через ffmpeg.
    
    Парсит вывод ffmpeg с флагом -progress pipe:1 и обновляет
    прогресс-бар tqdm на основе значения out_time_ms.
    """

    def __init__(self, total_seconds: float):
        """Инициализирует прогресс-трекер.
        
        Args:
            total_seconds: Общая длительность видео в секундах.
        """
        self.total_seconds = total_seconds
        self.pbar = tqdm(
            total=total_seconds,
            unit='s',
            desc='Синхронизация',
            ncols=100
        )
        # Регулярное выражение для поиска out_time_ms=
        self._time_pattern = re.compile(r'out_time_ms=(\d+)')

    def update(self, line: str):
        """Обновляет прогресс-бар на основе строки вывода ffmpeg.
        
        Args:
            line: Строка вывода из stdout ffmpeg.
            
        Метод ищет подстроку out_time_ms=, извлекает значение
        миллисекунд, преобразует в секунды и обновляет прогресс-бар.
        Если строка не содержит данных о времени или происходит
        ошибка преобразования типов, строка игнорируется.
        """
        try:
            match = self._time_pattern.search(line)
            if match:
                ms_value = int(match.group(1))
                current_seconds = ms_value / 1000.0
                self.pbar.n = current_seconds
                self.pbar.refresh()
        except (ValueError, AttributeError) as e:
            # Игнорируем ошибки парсинга, чтобы не останавливать процесс
            logger.debug(f'Ошибка парсинга строки прогресса: {e}')

    def close(self):
        """Корректно закрывает прогресс-бар и логирует завершение."""
        self.pbar.close()
        logger.info('Отслеживание прогресса завершено')
