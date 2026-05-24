package com.shoefactory.assistant.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExcelCellImageUtil {

    private static final Pattern DISPIMG_PATTERN = Pattern.compile(
            "(?:_xlfn\\.)?DISPIMG\\s*\\(\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private ExcelCellImageUtil() {
    }

    public static Optional<String> findCellImageId(Cell cell) {
        if (cell == null) {
            return Optional.empty();
        }
        String value = "";
        if (cell.getCellType() == CellType.FORMULA) {
            value = cell.getCellFormula();
        } else if (cell.getCellType() == CellType.STRING) {
            value = cell.getStringCellValue();
        }
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (normalized.startsWith("=")) {
            normalized = normalized.substring(1);
        }
        Matcher matcher = DISPIMG_PATTERN.matcher(normalized);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public static void clearCellImageFormulas(Sheet sheet) {
        for (Row row : sheet) {
            short firstCellNum = row.getFirstCellNum();
            short lastCellNum = row.getLastCellNum();
            if (firstCellNum < 0 || lastCellNum < 0) {
                continue;
            }
            for (int colIndex = firstCellNum; colIndex < lastCellNum; colIndex++) {
                Cell cell = row.getCell(colIndex);
                Optional<String> cellImageId = findCellImageId(cell);
                if (cellImageId.isEmpty()) {
                    continue;
                }
                cell.setBlank();
            }
        }
    }
}
