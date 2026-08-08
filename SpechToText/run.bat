@echo off
start %WINDIR%\System32\cmd.exe "/K" "C:\Users\Kolya\anaconda3\Scripts\activate.bat C:\Users\Kolya\anaconda3 && conda activate whisper_env && python main.py"
