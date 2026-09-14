package com.example.converter;

import com.example.converter.dto.CanonicalConditionDto;
import com.example.converter.dto.ConditionDto;
import com.example.converter.dto.GroupDto;
import com.example.converter.dto.InputContractDto;
import com.example.converter.dto.output.OutputConditionDto;
import com.example.converter.dto.output.OutputContractDto;
import com.example.converter.dto.output.OutputGroupDto;
import com.example.converter.parser.DcsFilterParser;
import com.example.converter.transformer.CanonicalConditionBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CLI-утилита «XML → JSON»: читает входной JSON-контракт, прогоняет каждое
 * условие через конвейер {@link DcsFilterParser} → {@link CanonicalConditionBuilder}
 * и сохраняет результат в новый файл {@code *_processed.json} рядом с входным.
 *
 * <p>Использование: {@code java -jar converter.jar <input-file.json>}
 *
 * <p>Выходной контракт повторяет входную структуру, но вместо сырой строки
 * {@code xmlFilter} каждое условие содержит типизированный объект {@code canonical}
 * ({@link CanonicalConditionDto}). Пустой или отсутствующий {@code xmlFilter}
 * даёт пустую каноническую модель ({@code {"logic": null, "rules": []}}) —
 * это штатная ситуация, а не ошибка контракта.
 */
public class Main {

    /** Код возврата при успешном завершении. */
    private static final int EXIT_SUCCESS = 0;

    /** Код возврата при любой ошибке. */
    private static final int EXIT_FAILURE = 1;

    private static final String USAGE = "Usage: java -jar converter.jar <input-file.json>";

    /** Суффикс выходного файла: {@code <имя_входного>_processed.json}. */
    private static final String OUTPUT_SUFFIX = "_processed.json";

    /** Общий {@link ObjectMapper} (потокобезопасен после конфигурации, переиспользуется). */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DcsFilterParser PARSER = new DcsFilterParser();

    private static final CanonicalConditionBuilder BUILDER = new CanonicalConditionBuilder();

    private Main() {
    }

    /**
     * Точка входа в CLI-утилиту.
     *
     * @param args ожидается ровно один аргумент — путь к входному JSON-файлу
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println(USAGE);
            System.exit(EXIT_FAILURE);
        }
        try {
            processFile(args[0]);
        } catch (CliException e) {
            // Контролируемая ошибка: сообщение уже готово к выводу как есть.
            System.err.println(e.getMessage());
            System.exit(EXIT_FAILURE);
        } catch (Exception e) {
            // IO, JSON parsing, XML parsing и прочие непредвиденные ошибки.
            System.err.println("Error: " + describe(e));
            System.exit(EXIT_FAILURE);
        }
        System.exit(EXIT_SUCCESS);
    }

    /**
     * Конвейер обработки файла: проверка существования → чтение → десериализация →
     * трансформация → запись результата.
     *
     * @param inputPath путь к входному JSON-файлу
     * @throws IOException               при ошибках чтения/записи файла и парсинга JSON
     * @throws CliException              если входной файл не существует
     * @throws IllegalArgumentException  если XML какого-либо условия не well-formed
     */
    private static void processFile(String inputPath) throws IOException {
        Path input = Path.of(inputPath);
        if (!Files.exists(input)) {
            throw new CliException("File not found: " + inputPath);
        }

        String json = Files.readString(input);
        InputContractDto contract = OBJECT_MAPPER.readValue(json, InputContractDto.class);

        OutputContractDto output = transform(contract);

        Path outputPath = buildOutputPath(input);
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), output);
        System.out.println("Success! Output saved to: " + outputPath);
    }

    // ------------------------------------------------------------------
    // Трансформация входных DTO в выходные
    // ------------------------------------------------------------------

    /**
     * Преобразует входной контракт в выходной: каждое условие проходит конвейер
     * парсер → билдер. Метод пакетной видимости: используется самим CLI и
     * интеграционным тестом {@code XmlToJsonPipelineIntegrationTest}.
     *
     * @param contract входной контракт с сырыми {@code xmlFilter}
     * @return выходной контракт с каноническими условиями
     */
    static OutputContractDto transform(InputContractDto contract) {
        List<OutputGroupDto> groups = new ArrayList<>();
        for (GroupDto group : contract.getGroups()) {
            groups.add(transformGroup(group));
        }
        return new OutputContractDto(contract.getContractId(), contract.getContractName(), groups);
    }

    static OutputGroupDto transformGroup(GroupDto group) {
        List<OutputConditionDto> conditions = new ArrayList<>();
        for (ConditionDto condition : group.getConditions()) {
            conditions.add(transformCondition(condition));
        }
        return new OutputGroupDto(group.getGroupId(), group.getGroupName(), conditions);
    }

    /**
     * Конвейер одного условия: парсер XML → нормализация в каноническую модель.
     * Пустой/отсутствующий {@code xmlFilter} даёт пустую модель (не ошибку).
     */
    static OutputConditionDto transformCondition(ConditionDto condition) {
        CanonicalConditionDto parsed = PARSER.parse(condition.getXmlFilter());
        CanonicalConditionDto canonical = BUILDER.build(parsed);
        return new OutputConditionDto(condition.getConditionId(), condition.getConditionName(), canonical);
    }

    // ------------------------------------------------------------------
    // Имя выходного файла
    // ------------------------------------------------------------------

    /**
     * Формирует путь выходного файла: имя входного файла без расширения
     * {@code .json} + суффикс {@code _processed.json}, в той же директории.
     */
    private static Path buildOutputPath(Path input) {
        String fileName = input.getFileName().toString();
        String baseName = fileName.toLowerCase(Locale.ROOT).endsWith(".json")
                ? fileName.substring(0, fileName.length() - ".json".length())
                : fileName;
        String outputName = baseName + OUTPUT_SUFFIX;
        Path parent = input.getParent();
        return parent == null ? Path.of(outputName) : parent.resolve(outputName);
    }

    // ------------------------------------------------------------------
    // Вспомогательные операции
    // ------------------------------------------------------------------

    /** Человекочитаемое описание исключения для вывода в stderr. */
    private static String describe(Throwable error) {
        String message = error.getMessage();
        return (message == null || message.isBlank())
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
    }

    /** Контролируемая ошибка CLI: сообщение выводится в stderr без префиксов. */
    private static final class CliException extends RuntimeException {
        CliException(String message) {
            super(message);
        }
    }
}