"""CLI интерфейс для синхронизации аудио и видео."""

import os
import sys

import click

from config.settings import check_ffmpeg_binaries
from core.synchronizer import Synchronizer
from utils.logger import logger


@click.command()
@click.option('--audio', type=str, help='Путь к аудиофайлу')
@click.option('--video', type=str, help='Путь к видеофайлу')
@click.option('--offset', type=float, help='Смещение в секундах')
def cli(audio: str, video: str, offset: float):
    """Синхронизация аудио и видео файлов с помощью FFmpeg."""
    
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
    
    # Проверка бинарников FFmpeg
    if not check_ffmpeg_binaries():
        click.echo(click.style(
            'Ошибка: FFmpeg не найден. Пожалуйста, установите FFmpeg и настройте пути в config/settings.py',
            fg='red'
        ))
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
    
    # Запуск синхронизации
    try:
        synchronizer = Synchronizer(video_path=video, audio_path=audio, offset=offset)
        output_path = synchronizer.run()
        click.echo(click.style(
            f'\nГотово! Файл сохранен по пути: {output_path}',
            fg='green'
        ))
    except Exception as e:
        click.echo(click.style(f'\nОшибка: {e}', fg='red'))
        logger.exception('Ошибка при синхронизации')
        ctx = click.get_current_context()
        ctx.exit(1)


if __name__ == '__main__':
    cli()
