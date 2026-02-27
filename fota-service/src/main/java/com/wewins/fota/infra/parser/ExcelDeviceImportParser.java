package com.wewins.fota.infra.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.springframework.stereotype.Component;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel文件设备导入解析器
 * <p>
 * 使用Apache POI解析Excel文件（.xlsx、.xls）
 * 查找第一行中名为"IMEI"的列（不区分大小写），提取该列所有非空值
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-27
 */
@Slf4j
@Component
public class ExcelDeviceImportParser implements DeviceImportFileParser {

    private static final String TARGET_COLUMN_NAME = "IMEI";

    @Override
    public List<String> parse(InputStream inputStream) throws IOException {
        List<String> imeis = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IOException("Excel文件第一行为空，无法找到IMEI列");
            }

            // 查找IMEI列的索引
            int imeiColumnIndex = findImeiColumnIndex(headerRow);
            if (imeiColumnIndex == -1) {
                throw new IOException("Excel文件第一行必须包含名为\"" + TARGET_COLUMN_NAME + "\"的列");
            }

            log.info("找到IMEI列，索引: {}", imeiColumnIndex);

            // 遍历数据行，提取IMEI
            int rowCount = sheet.getPhysicalNumberOfRows();
            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Cell cell = row.getCell(imeiColumnIndex);
                if (cell == null) {
                    continue;
                }

                String imei = getCellValueAsString(cell);
                if (imei != null && !imei.isEmpty()) {
                    imeis.add(imei);

                    if (log.isDebugEnabled()) {
                        log.debug("解析Excel文件第{}行: IMEI={}", i + 1, imei);
                    }
                }
            }

            log.info("Excel文件解析完成，共提取{}个IMEI", imeis.size());
        } catch (IOException e) {
            log.error("Excel文件解析失败", e);
            throw e;
        }

        return imeis;
    }

    /**
     * 查找IMEI列的索引（不区分大小写）
     *
     * @param headerRow 表头行
     * @return 列索引，未找到返回-1
     */
    private int findImeiColumnIndex(Row headerRow) {
        short lastCellNum = headerRow.getLastCellNum();

        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) {
                continue;
            }

            String cellValue = getCellValueAsString(cell);
            if (cellValue != null && TARGET_COLUMN_NAME.equalsIgnoreCase(cellValue.trim())) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 获取单元格的字符串值
     *
     * @param cell 单元格
     * @return 字符串值
     */
    private String getCellValueAsString(Cell cell) {
        CellType cellType = cell.getCellType();

        return switch (cellType) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                // 避免科学计数法，将数字转为字符串
                double numericValue = cell.getNumericCellValue();
                if (numericValue == (long) numericValue) {
                    yield String.valueOf((long) numericValue);
                } else {
                    yield String.valueOf(numericValue);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield cell.getNumericCellValue() + "";
                }
            }
            default -> null;
        };
    }
}
