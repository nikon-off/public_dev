"""CLI интерфейс для синхронизации аудио и видео."""

import os
import sys
import traceback

import click

from config.settings import check_ffmpeg_binaries
from core.synchronizer import Synchronizer
from utils.logger import logger


class SyncContext:
    """Контекст для хранения состояния синхронизации."""
    
    def __init__(self):
        self.process = None
        self.tracker = None
        self.output_path = None


@click.command()
@click.option('--audio', type=str, help='Путь к аудиофайлу')
@click.option('--video', type=str, help='Путь к видеофайлу')
@click.option('--offset', type=float, help='Смещение в секундах')
def cli(audio: str, video: str, offset: float):
    """Синхронизация аудио и видео файлов с помощью FFmpeg."""
    
    # Инициализация логгера самым первым (уже импортирован выше)
    logger.info('Запуск CLI приложения')
    
    # Контекст для отслеживания состояния
    sync_context = SyncContext()
    
    # Интерактивный режим: если ни одна из опций не передана
    if audio is None and video is None and offset is None:
        click.echo('\n=== Интерактивный режим синхронизации ===\n')
        
        # Запрос путей к файлам с проверкой существования
        while True:
            video = click.prompt('Введите путь к видеофайлу', type=str)
            if not os.path.exists(video):
                click.echo(click.style(f'Ошибка: Файл не найден: {video}', fg='red'))
            else:
                break
        
        while True:
            audio = click.prompt('Введите путь к аудиофайлу', type=str)
            if not os.path.exists(audio):
                click.echo(click.style(f'Ошибка: Файл не найден: {audio}', fg='red'))
            else:
                break
        
        # Запрос смещения с дефолтным значением 0.0
        offset = click.prompt(
            'Введите смещение в секундах (или нажмите Enter для 0.0)',
            type=float,
            default=0.0
        )
    
    # Проверка бинарников FFmpeg ДО начала любых тяжелых операций
    if not check_ffmpeg_binaries():
        click.echo(click.style(
            'Ошибка: FFmpeg не найден. Пожалуйста, установите FFmpeg и настройте пути в config/settings.py',
            fg='red'
        ))
        logger.error('FFmpeg binaries не найдены')
        ctx = click.get_current_context()
        ctx.exit(1)
    
    # Сводка параметров
    click.echo('\n=== Параметры синхронизации ===')
    click.echo(f'Видеофайл: {video}')
    click.echo(f'Аудиофайл: {audio}')
    click.echo(f'Смещение:  {offset} сек.')
    click.echo('')
    
    # Подтверждение начала
    if not click.confirm('Начать синхронизацию?'):
        click.echo('Синхронизация отменена пользователем.')
        ctx = click.get_current_context()
        ctx.exit(0)
    
    def cleanup_partial_output():
        """Удаление частично созданного выходного файла."""
        if sync_context.output_path and os.path.exists(sync_context.output_path):
            try:
                os.remove(sync_context.output_path)
                logger.info(f'Удален частичный файл: {sync_context.output_path}')
                click.echo(click.style(f'Удален частичный файл: {sync_context.output_path}', fg='yellow'))
            except Exception as e:
                logger.error(f'Не удалось удалить частичный файл: {e}')
    
    def handle_interrupt():
        """Обработка прерывания операции."""
        click.echo(click.style('\nОперация прервана пользователем', fg='yellow'))
        logger.warning('Операция прервана пользователем')
        
        # Закрываем прогресс-бар
        if sync_context.tracker is not None:
            try:
                sync_context.tracker.close()
            except Exception:
                pass
        
        # Завершаем subprocess если он еще работает
        if sync_context.process is not None:
            try:
                if sync_context.process.poll() is None:
                    sync_context.process.terminate()
                    try:
                        sync_context.process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        sync_context.process.kill()
                        sync_context.process.wait()
                    logger.info('FFmpeg процесс завершен')
            except Exception as e:
                logger.error(f'Ошибка при завершении процесса: {e}')
        
        # Удаляем частично созданный файл
        cleanup_partial_output()
    
    # Запуск синхронизации с обработкой исключений
    try:
        synchronizer = Synchronizer(video_path=video, audio_path=audio, offset=offset)
        
        # Передаем контекст для отслеживания процесса
        synchronizer.set_context_callback(lambda proc, tracker: setattr(sync_context, 'process', proc) or setattr(sync_context, 'tracker', tracker))
        synchronizer.set_output_callback(lambda path: setattr(sync_context, 'output_path', path))
        
        output_path = synchronizer.run()
        click.echo(click.style(
            f'\nГотово! Файл сохранен по пути: {output_path}',
            fg='green'
        ))
        logger.info(f'Синхронизация успешно завершена: {output_path}')
        
    except FileNotFoundError as e:
        click.echo(click.style('Ошибка: один из указанных файлов не найден', fg='red'))
        logger.exception('FileNotFoundError: файл не найден')
        ctx = click.get_current_context()
        ctx.exit(1)
        
    except ValueError as e:
        click.echo(click.style(f'Ошибка валидации: {e}', fg='red'))
        logger.exception(f'ValueError: {e}')
        ctx = click.get_current_context()
        ctx.exit(1)
        
    except MemoryError as e:
        click.echo(click.style('Критическая ошибка: превышен лимит оперативной памяти', fg='red'))
        logger.exception('MemoryError: превышен лимит памяти')
        cleanup_partial_output()
        ctx = click.get_current_context()
        ctx.exit(1)
        
    except RuntimeError as e:
        error_msg = str(e)
        if 'памяти' in error_msg.lower() or 'memory' in error_msg.lower():
            click.echo(click.style('Критическая ошибка: превышен лимит оперативной памяти', fg='red'))
            logger.exception('RuntimeError: превышен лимит памяти')
        else:
            click.echo(click.style(f'Ошибка выполнения: {e}', fg='red'))
            logger.exception(f'RuntimeError: {e}')
        cleanup_partial_output()
        ctx = click.get_current_context()
        ctx.exit(1)
        
    except KeyboardInterrupt:
        handle_interrupt()
        ctx = click.get_current_context()
        ctx.exit(1)
        
    except Exception as e:
        # Логирование полного стектрейса
        logger.exception('Произошла непредвиденная ошибка')
        click.echo(click.style('Произошла непредвиденная ошибка. Подробности в syncer.log', fg='red'))
        cleanup_partial_output()
        ctx = click.get_current_context()
        ctx.exit(1)


if __name__ == '__main__':
    cli()
