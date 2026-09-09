"""Модуль логирования для проекта av_skleyka.

Обеспечивает вывод сообщений на русском языке в консоль и файл.
"""

import logging


def setup_logger() -> logging.Logger:
    """Настраивает и возвращает логгер с именем 'syncer'.
    
    Конфигурация включает:
    - ConsoleHandler: уровень INFO, формат [%(levelname)s] %(message)s
    - FileHandler: уровень DEBUG, запись в syncer.log с перезаписью
    
    Returns:
        logging.Logger: Настроенный объект логгера.
    """
    logger = logging.getLogger('syncer')
    
    # Проверка на наличие хендлеров для предотвращения дублирования
    if not logger.handlers:
        logger.setLevel(logging.DEBUG)
        
        # Хендлер 1: Консоль (INFO уровень)
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        console_formatter = logging.Formatter(
            '[%(levelname)s] %(message)s'
        )
        console_handler.setFormatter(console_formatter)
        logger.addHandler(console_handler)
        
        # Хендлер 2: Файл (DEBUG уровень, перезапись при каждом запуске)
        file_handler = logging.FileHandler('syncer.log', mode='w', encoding='utf-8')
        file_handler.setLevel(logging.DEBUG)
        file_formatter = logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(filename)s:%(lineno)d - %(message)s'
        )
        file_handler.setFormatter(file_formatter)
        logger.addHandler(file_handler)
        
        # Сообщение об успешной инициализации на русском языке
        logger.info('Логгер успешно инициализирован')
        logger.debug('Консольный хендлер настроен на уровень INFO')
        logger.debug('Файловый хендлер настроен на уровень DEBUG (syncer.log)')
    
    return logger


# Экспорт готового объекта логгера
logger = setup_logger()
