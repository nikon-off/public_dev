#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Скрипт автоматической настройки окружения для проекта av_skleyka.

Запуск: python setup_env.py
"""

import subprocess
import sys
import os


def check_python_version() -> bool:
    """
    Проверка версии Python (требуется >= 3.8).
    
    Returns:
        True если версия подходит, False иначе.
    """
    print("=" * 50)
    print("Шаг 1: Проверка версии Python")
    print("=" * 50)
    
    current_version = sys.version_info
    version_str = f"{current_version.major}.{current_version.minor}.{current_version.micro}"
    
    if current_version.major < 3 or (current_version.major == 3 and current_version.minor < 8):
        print(f"Ошибка: Требуется Python >= 3.8, у вас {version_str}")
        print("Пожалуйста, установите Python версии 3.8 или выше.")
        return False
    
    print(f"Версия Python: {version_str} — OK")
    return True


def install_dependencies() -> bool:
    """
    Установка зависимостей проекта.
    
    Returns:
        True если установка успешна, False иначе.
    """
    print("\n" + "=" * 50)
    print("Шаг 2: Установка зависимостей")
    print("=" * 50)
    
    # Проверяем, доступен ли pip
    try:
        subprocess.run(
            [sys.executable, "-m", "pip", "--version"],
            check=True,
            capture_output=True,
            text=True
        )
    except subprocess.CalledProcessError:
        print("Ошибка: pip не найден или не доступен в PATH.")
        print("Пожалуйста, установите pip и попробуйте снова.")
        return False
    except FileNotFoundError:
        print("Ошибка: модуль pip не найден.")
        print("Пожалуйста, убедитесь, что pip установлен корректно.")
        return False
    
    # Определяем текущую директорию скрипта
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    # Пробуем установить через pyproject.toml (предпочтительно)
    pyproject_path = os.path.join(script_dir, "pyproject.toml")
    requirements_path = os.path.join(script_dir, "requirements.txt")
    
    install_cmd = None
    use_requirements = False
    
    if os.path.exists(pyproject_path):
        print("Обнаружен pyproject.toml, пробуем 'pip install -e .'")
        install_cmd = [sys.executable, "-m", "pip", "install", "-e", script_dir]
    elif os.path.exists(requirements_path):
        print("Используем requirements.txt: 'pip install -r requirements.txt'")
        install_cmd = [sys.executable, "-m", "pip", "install", "-r", requirements_path]
        use_requirements = True
    else:
        print("Ошибка: Не найден ни pyproject.toml, ни requirements.txt")
        return False
    
    print("\nПроцесс установки зависимостей:")
    print("-" * 50)
    
    try:
        result = subprocess.run(
            install_cmd,
            check=True,
            capture_output=False,
            text=True
        )
        print("-" * 50)
        print("Зависимости успешно установлены!")
        return True
    except subprocess.CalledProcessError as e:
        # Если установка через pyproject.toml не удалась, пробуем requirements.txt
        if not use_requirements and os.path.exists(requirements_path):
            print("-" * 50)
            print("Установка через pyproject.toml не удалась.")
            print("Пробуем установку через requirements.txt...")
            print("-" * 50)
            
            install_cmd = [sys.executable, "-m", "pip", "install", "-r", requirements_path]
            try:
                subprocess.run(
                    install_cmd,
                    check=True,
                    capture_output=False,
                    text=True
                )
                print("-" * 50)
                print("Зависимости успешно установлены через requirements.txt!")
                return True
            except subprocess.CalledProcessError:
                print("-" * 50)
                print("Ошибка при установке зависимостей через requirements.txt.")
                return False
        else:
            print("-" * 50)
            print(f"Ошибка при установке зависимостей: {e}")
            print("Проверьте логи выше для получения подробностей.")
            return False


def check_ffmpeg() -> bool:
    """
    Проверка наличия бинарников FFmpeg.
    
    Returns:
        True если бинарники найдены, False иначе.
    """
    print("\n" + "=" * 50)
    print("Шаг 3: Проверка FFmpeg")
    print("=" * 50)
    
    # Добавляем корневую директорию проекта в путь импорта
    script_dir = os.path.dirname(os.path.abspath(__file__))
    if script_dir not in sys.path:
        sys.path.insert(0, script_dir)
    
    try:
        from config.settings import check_ffmpeg_binaries
        
        if check_ffmpeg_binaries():
            print("FFmpeg найден — OK")
            return True
        else:
            print("\nВнимание: FFmpeg не найден по пути C:\\ffmpeg\\bin\\")
            print("Пожалуйста, скачайте FFmpeg и распакуйте его в эту папку,")
            print("либо измените путь в config/settings.py")
            return False
    except ImportError as e:
        print(f"Предупреждение: Не удалось импортировать config.settings: {e}")
        print("Проверка FFmpeg будет пропущена.")
        return True


def test_import() -> bool:
    """
    Тестовый импорт главного модуля.
    
    Returns:
        True если импорт успешен, False иначе.
    """
    print("\n" + "=" * 50)
    print("Шаг 4: Тестовый запуск")
    print("=" * 50)
    
    # Добавляем корневую директорию проекта в путь импорта
    script_dir = os.path.dirname(os.path.abspath(__file__))
    if script_dir not in sys.path:
        sys.path.insert(0, script_dir)
    
    try:
        import main
        print("Импорт модуля main — OK")
        print("\n" + "=" * 50)
        print("Окружение настроено успешно!")
        print("Теперь вы можете запустить синхронизатор командой: sync-av")
        print("=" * 50)
        return True
    except ImportError as e:
        print(f"Ошибка импорта модуля main: {e}")
        print("Проверьте, что все зависимости установлены корректно.")
        return False
    except Exception as e:
        print(f"Непредвиденная ошибка при импорте: {e}")
        return False


def main():
    """Основная функция настройки окружения."""
    print("\n" + "#" * 50)
    print("#  Скрипт настройки окружения av_skleyka")
    print("#" * 50)
    
    # Шаг 1: Проверка Python
    if not check_python_version():
        print("\nНастройка завершена с ошибкой.")
        sys.exit(1)
    
    # Шаг 2: Установка зависимостей
    if not install_dependencies():
        print("\nНастройка завершена с ошибкой.")
        sys.exit(1)
    
    # Шаг 3: Проверка FFmpeg
    check_ffmpeg()
    
    # Шаг 4: Тестовый запуск
    if not test_import():
        print("\nНастройка завершена с предупреждениями.")
        sys.exit(1)
    
    print("\nВсе шаги выполнены успешно!")
    sys.exit(0)


if __name__ == "__main__":
    main()
