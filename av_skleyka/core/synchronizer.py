"""Модуль оркестрации процесса синхронизации аудио и видео."""

import subprocess
from typing import Optional, Callable

from config.settings import MAX_RAM_GB
from core.media_processor import build_ffmpeg_command, get_duration
from core.progress_tracker import ProgressTracker
from utils.file_utils import generate_output_path
from utils.logger import logger
from utils.memory_monitor import MemoryMonitor
from utils.validators import validate_file_exists, validate_offset


class Synchronizer:
    """Класс-оркестратор для управления процессом синхронизации видео и аудио.

    Выполняет полную цепочку действий:
    1. Валидация входных файлов
    2. Получение метаданных
    3. Проверка смещения
    4. Подготовка выходного пути
    5. Построение команды ffmpeg
    6. Запуск процесса с отслеживанием прогресса и памяти
    7. Завершение и проверка результата

    Attributes:
        video_path: Путь к видеофайлу.
        audio_path: Путь к аудиофайлу.
        offset: Смещение аудио относительно видео в секундах.
        memory_monitor: Экземпляр монитора памяти.
        _context_callback: Callback для передачи process и tracker наружу.
        _output_callback: Callback для передачи output_path наружу.
    """

    def __init__(self, video_path: str, audio_path: str, offset: float):
        """Инициализирует синхронизатор.

        Args:
            video_path: Путь к видеофайлу.
            audio_path: Путь к аудиофайлу.
            offset: Смещение аудио относительно видео в секундах.
        """
        self.video_path = video_path
        self.audio_path = audio_path
        self.offset = offset
        self.memory_monitor = MemoryMonitor()
        self._context_callback: Optional[Callable] = None
        self._output_callback: Optional[Callable] = None

    def set_context_callback(self, callback: Callable):
        """Устанавливает callback для передачи process и tracker.

        Args:
            callback: Функция, принимающая (process, tracker).
        """
        self._context_callback = callback

    def set_output_callback(self, callback: Callable):
        """Устанавливает callback для передачи output_path.

        Args:
            callback: Функция, принимающая output_path.
        """
        self._output_callback = callback

    def run(self) -> str:
        """Запускает полный процесс синхронизации.

        Returns:
            Путь к созданному файлу.

        Raises:
            RuntimeError: При ошибке валидации, получения метаданных,
                превышении лимита памяти или ошибке ffmpeg.
        """
        tracker: Optional[ProgressTracker] = None
        process: Optional[subprocess.Popen] = None

        try:
            # Шаг 1: Валидация файлов
            logger.info('Начало валидации файлов...')
            validate_file_exists(self.video_path)
            validate_file_exists(self.audio_path)
            logger.info('Валидация файлов успешно завершена')

            # Шаг 2: Получение метаданных
            logger.info('Получение метаданных...')
            video_duration = get_duration(self.video_path)
            logger.info(f'Длительность видео: {video_duration} сек.')

            # Шаг 3: Проверка смещения
            logger.info('Проверка смещения...')
            validate_offset(self.offset, video_duration)
            logger.info('Смещение проверено успешно')

            # Шаг 4: Подготовка выходного пути
            logger.info('Подготовка выходного файла...')
            output_path = generate_output_path(self.video_path)
            logger.info(f'Выходной файл: {output_path}')

            # Шаг 5: Построение команды ffmpeg
            logger.info('Построение команды ffmpeg...')
            cmd = build_ffmpeg_command(
                self.video_path,
                self.audio_path,
                self.offset,
                output_path
            )

            # Шаг 6: Запуск процесса
            logger.info('Запуск синхронизации...')
            tracker = ProgressTracker(video_duration)

            # ИСПРАВЛЕНИЕ: Читаем stderr, так как ffmpeg пишет прогресс туда
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,  # Вывод нам не нужен
                stderr=subprocess.PIPE,     # Прогресс и ошибки идут сюда
                text=True,
                shell=False
            )

            # Передаем процесс и трекер через callback для внешней обработки прерываний
            if self._context_callback is not None:
                self._context_callback(process, tracker)

            line_count = 0
            # ИСПРАВЛЕНИЕ: Читаем stderr построчно, чтобы не переполнять буфер
            for line in process.stderr:
                # ИСПРАВЛЕНИЕ: Передаем сырую строку в трекер, он сам распарсит
                tracker.update(line)
                
                line_count += 1

                # Каждые 100 строк проверяем лимит памяти
                if line_count % 100 == 0:
                    if not self.memory_monitor.check_limit():
                        logger.critical('Превышен лимит памяти, принудительное завершение')
                        process.kill()
                        raise RuntimeError('Превышен лимит оперативной памяти')

            # Шаг 7: Завершение процесса
            process.wait()

            # Проверка кода возврата
            if process.returncode != 0:
                # Читаем остаток stderr только если процесс упал
                remaining_error = process.stderr.read()
                error_msg = f'FFmpeg завершился с кодом {process.returncode}'
                if remaining_error:
                    error_msg += f'. Детали: {remaining_error.strip()}'
                logger.error(error_msg)
                raise RuntimeError(error_msg)

            logger.info('Синхронизация успешно завершена')
            
            # Передаем output_path через callback
            if self._output_callback is not None:
                self._output_callback(output_path)
            
            return output_path

        finally:
            # Гарантированное закрытие прогресс-бара
            if tracker is not None:
                tracker.close()
            # Гарантированное освобождение ресурсов процесса
            if process is not None and process.poll() is None:
                process.kill()
                process.wait()