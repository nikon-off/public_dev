"""Unit tests for media_processor module."""

import subprocess
import pytest
from unittest.mock import patch, MagicMock

from core.media_processor import get_duration, build_ffmpeg_command


class TestGetDuration:
    """Tests for get_duration function."""

    @patch('core.media_processor.subprocess.run')
    def test_get_duration_returns_float(self, mock_run):
        """Test that function returns correct duration as float."""
        # Setup mock to return a successful result with duration
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "120.5\n"
        mock_result.stderr = ""
        mock_run.return_value = mock_result

        result = get_duration("/path/to/video.mp4")

        assert result == 120.5
        assert isinstance(result, float)

    @patch('core.media_processor.subprocess.run')
    def test_subprocess_run_called_with_correct_args(self, mock_run):
        """Test that subprocess.run is called with correct arguments."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "120.5\n"
        mock_result.stderr = ""
        mock_run.return_value = mock_result

        test_path = "/path/to/video.mp4"
        get_duration(test_path)

        # Verify subprocess.run was called
        mock_run.assert_called_once()
        call_args = mock_run.call_args

        # Check the command list (first positional argument)
        cmd = call_args[0][0]
        assert '-v' in cmd
        assert 'error' in cmd
        assert '-show_entries' in cmd
        assert 'format=duration' in cmd
        assert '-of' in cmd
        assert 'default=noprint_wrappers=1:nokey=1' in cmd
        assert test_path in cmd

        # Check keyword arguments
        kwargs = call_args[1]
        assert kwargs['capture_output'] is True
        assert kwargs['text'] is True
        assert kwargs['check'] is False

    @patch('core.media_processor.subprocess.run')
    def test_get_duration_raises_on_nonzero_returncode(self, mock_run):
        """Test that function raises RuntimeError when ffprobe fails."""
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_result.stdout = ""
        mock_result.stderr = "File not found"
        mock_run.return_value = mock_result

        with pytest.raises(RuntimeError) as exc_info:
            get_duration("/path/to/nonexistent.mp4")

        assert 'Ошибка ffprobe' in str(exc_info.value)

    @patch('core.media_processor.subprocess.run')
    def test_get_duration_raises_on_empty_output(self, mock_run):
        """Test that function raises RuntimeError when output is empty."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = ""
        mock_result.stderr = ""
        mock_run.return_value = mock_result

        with pytest.raises(RuntimeError) as exc_info:
            get_duration("/path/to/video.mp4")

        assert 'пустой вывод' in str(exc_info.value)

    @patch('core.media_processor.subprocess.run')
    def test_get_duration_raises_on_invalid_duration_format(self, mock_run):
        """Test that function raises RuntimeError when duration cannot be parsed."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = "invalid_duration\n"
        mock_result.stderr = ""
        mock_run.return_value = mock_result

        with pytest.raises(RuntimeError) as exc_info:
            get_duration("/path/to/video.mp4")

        assert 'Не удалось распарсить' in str(exc_info.value)


class TestBuildFFmpegCommand:
    """Tests for build_ffmpeg_command function."""

    def test_build_ffmpeg_command_contains_required_args(self):
        """Test that command contains all required ffmpeg arguments."""
        video_path = "/path/to/video.mp4"
        audio_path = "/path/to/audio.mp3"
        offset = 5.0
        output_path = "/path/to/output.mp4"

        cmd = build_ffmpeg_command(video_path, audio_path, offset, output_path)

        # Check basic structure
        assert len(cmd) > 0

        # Check required flags are present
        assert '-itsoffset' in cmd
        assert '5.0' in cmd
        assert '-c:v' in cmd
        assert 'copy' in cmd
        assert '-map' in cmd
        assert '0:v:0' in cmd
        assert '1:a:0' in cmd
        assert '-c:a' in cmd
        assert '-y' in cmd
        assert '-progress' in cmd
        assert 'pipe:1' in cmd

    def test_build_ffmpeg_command_correct_order(self):
        """Test that arguments are in correct order."""
        video_path = "/path/to/video.mp4"
        audio_path = "/path/to/audio.mp3"
        offset = 5.0
        output_path = "/path/to/output.mp4"

        cmd = build_ffmpeg_command(video_path, audio_path, offset, output_path)

        # -itsoffset should come before the video input
        itsoffset_idx = cmd.index('-itsoffset')
        offset_val_idx = cmd.index('5.0')
        video_input_idx = cmd.index('-i')
        
        assert itsoffset_idx < offset_val_idx
        assert offset_val_idx < video_input_idx

        # Check that video path follows first -i
        assert cmd[video_input_idx + 1] == video_path

        # Check output path is at the end
        assert cmd[-1] == output_path

    def test_build_ffmpeg_command_includes_paths(self):
        """Test that all paths are included in command."""
        video_path = "/path/to/video.mp4"
        audio_path = "/path/to/audio.mp3"
        offset = 3.5
        output_path = "/path/to/output.mp4"

        cmd = build_ffmpeg_command(video_path, audio_path, offset, output_path)

        assert video_path in cmd
        assert audio_path in cmd
        assert output_path in cmd

    def test_build_ffmpeg_command_offset_as_string(self):
        """Test that offset is converted to string."""
        video_path = "/path/to/video.mp4"
        audio_path = "/path/to/audio.mp3"
        offset = 5.0
        output_path = "/path/to/output.mp4"

        cmd = build_ffmpeg_command(video_path, audio_path, offset, output_path)

        # Offset value should be string in the command
        assert '5.0' in cmd or '5' in cmd
