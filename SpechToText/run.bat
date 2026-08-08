@echo off
cd /d "%~dp0"
cmd /K "C:\Users\Kolya\anaconda3\Scripts\activate.bat C:\Users\Kolya\anaconda3 && call conda activate whisper_env && python main.py"
