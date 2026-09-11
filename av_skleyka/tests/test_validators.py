"""Unit tests for validators module."""

import logging
import pytest
from unittest.mock import patch

from utils.validators import validate_file_exists, validate_offset


class TestValidateFileExists:
    """Tests for validate_file_exists function."""

    def test_file_exists_returns_true(self, tmp_path):
        """Test that function returns True when file exists."""
        # Create a temporary file
        test_file = tmp_path / "test_file.txt"
        test_file.write_text("test content")

        result = validate_file_exists(str(test_file))

        assert result is True

    def test_file_not_exists_returns_false_and_logs_error(self, tmp_path):
        """Test that function raises FileNotFoundError and logs error for non-existent file."""
        non_existent_file = str(tmp_path / "non_existent.txt")

        with pytest.raises(FileNotFoundError) as exc_info:
            with patch('utils.validators.logger') as mock_logger:
                validate_file_exists(non_existent_file)
                # Verify logger.error was called
                mock_logger.error.assert_called_once()
                call_args = mock_logger.error.call_args[0][0]
                assert 'Файл не найден' in call_args
                assert non_existent_file in call_args

        assert non_existent_file in str(exc_info.value)


class TestValidateOffset:
    """Tests for validate_offset function."""

    def test_valid_offset_returns_true(self):
        """Test that function returns True for valid offset (offset < duration)."""
        result = validate_offset(offset=5.0, video_duration=120.0)
        assert result is True

    def test_offset_equals_duration_raises_value_error(self):
        """Test that function raises ValueError when offset >= duration."""
        with pytest.raises(ValueError) as exc_info:
            validate_offset(offset=120.0, video_duration=120.0)

        assert 'не может быть больше или равно' in str(exc_info.value)

    def test_offset_greater_than_duration_raises_value_error(self):
        """Test that function raises ValueError when offset > duration."""
        with pytest.raises(ValueError) as exc_info:
            validate_offset(offset=150.0, video_duration=120.0)

        assert 'не может быть больше или равно' in str(exc_info.value)

    def test_negative_offset_raises_value_error(self):
        """Test that function raises ValueError for negative offset."""
        with pytest.raises(ValueError) as exc_info:
            validate_offset(offset=-5.0, video_duration=120.0)

        assert 'не может быть отрицательным' in str(exc_info.value)

    def test_zero_offset_returns_true(self):
        """Test that zero offset is valid."""
        result = validate_offset(offset=0.0, video_duration=120.0)
        assert result is True
