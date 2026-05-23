package com.baletpos.service;

import com.baletpos.dao.ProductDAO;
import com.baletpos.model.Product;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ExcelImportService {
    private static final Logger logger = LoggerFactory.getLogger(ExcelImportService.class);
    private final ProductDAO productDAO = new ProductDAO();

    /**
     * Generate template Excel file for product import
     */
    public void generateTemplate(File outputFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Products");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "SKU", "Nama Produk", "Tipe Produk", "HPP", "Margin (%)",
                    "Harga Jual", "Stok", "Deskripsi"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Instruction row style
            CellStyle noteStyle = workbook.createCellStyle();
            noteStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            noteStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font noteFont = workbook.createFont();
            noteFont.setItalic(true);
            noteStyle.setFont(noteFont);

            // Add instruction row
            Row instructionRow = sheet.createRow(1);
            String[] instructions = {
                    "Contoh: LPT-001",
                    "Contoh: ASUS VivoBook 14",
                    "LAPTOP_NEW / LAPTOP_SECOND / SPAREPARTS / PERIPHERAL / SERVICE",
                    "Contoh: 5000000",
                    "Contoh: 15",
                    "Contoh: 5750000 (atau kosong jika auto)",
                    "Contoh: 10",
                    "Deskripsi produk"
            };

            for (int i = 0; i < instructions.length; i++) {
                Cell cell = instructionRow.createCell(i);
                cell.setCellValue(instructions[i]);
                cell.setCellStyle(noteStyle);
            }

            // Add sample data rows
            String[][] sampleData = {
                    { "LPT-NEW-001", "ASUS VivoBook 14 X1402ZA i3-1215U 8GB 512GB", "LAPTOP_NEW", "7500000", "12", "",
                            "5", "Laptop baru garansi resmi" },
                    { "LPT-SEC-001", "Lenovo ThinkPad T480 i5-8350U 8GB 256GB", "LAPTOP_SECOND", "4500000", "20", "",
                            "3", "Laptop bekas mulus grade A" },
                    { "SPR-RAM-001", "DDR4 8GB 3200MHz", "SPAREPARTS", "350000", "25", "", "20",
                            "RAM laptop sodimm" },
                    { "SPR-SSD-001", "SSD NVMe 512GB", "SPAREPARTS", "450000", "20", "", "15", "SSD M.2 2280" },
                    { "PRF-MSE-001", "Logitech M185 Wireless Mouse", "PERIPHERAL", "120000", "25", "", "30",
                            "Mouse wireless" },
                    { "SVC-INS-001", "Jasa Install Windows + Office", "SERVICE", "50000", "100", "", "999",
                            "Jasa instalasi sistem operasi" }
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 2);
                for (int c = 0; c < sampleData[r].length; c++) {
                    row.createCell(c).setCellValue(sampleData[r][c]);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            logger.info("Template Excel berhasil dibuat: " + outputFile.getAbsolutePath());
        }
    }

    /**
     * Import products from Excel file
     */
    public ImportResult importProducts(File excelFile) throws IOException {
        ImportResult result = new ImportResult();

        try (FileInputStream fis = new FileInputStream(excelFile);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            // Skip header and instruction rows (start from row 2)
            for (int rowNum = 2; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null)
                    continue;

                try {
                    Product importedProduct = parseProductRow(row);
                    if (importedProduct != null && importedProduct.getSku() != null
                            && !importedProduct.getSku().isBlank()) {
                        var existingProduct = productDAO.findBySku(importedProduct.getSku());
                        if (existingProduct.isPresent()) {
                            Product productToUpdate = mergeImportedProduct(existingProduct.get(), importedProduct);
                            productDAO.save(productToUpdate);
                            result.addUpdated(importedProduct.getSku());
                        } else {
                            productDAO.save(importedProduct);
                            result.addSuccess(importedProduct.getSku());
                        }
                    }
                } catch (Exception e) {
                    result.addFailed(rowNum + 1, e.getMessage());
                }
            }
        }

        return result;
    }

    private Product mergeImportedProduct(Product existingProduct, Product importedProduct) {
        existingProduct.setName(importedProduct.getName());
        existingProduct.setProductType(importedProduct.getProductType());
        existingProduct.setHpp(importedProduct.getHpp());
        existingProduct.setMarginPercent(importedProduct.getMarginPercent());
        existingProduct.setSellingPrice(importedProduct.getSellingPrice());
        existingProduct.setStock(importedProduct.getStock());
        existingProduct.setDescription(importedProduct.getDescription());
        existingProduct.setActive(true);
        return existingProduct;
    }

    private Product parseProductRow(Row row) {
        Product product = new Product();

        // SKU (required)
        String sku = getCellStringValue(row.getCell(0));
        if (sku == null || sku.isBlank())
            return null;
        product.setSku(sku.trim());

        // Name (required)
        String name = getCellStringValue(row.getCell(1));
        if (name == null || name.isBlank())
            return null;
        product.setName(name.trim());

        // Product Type (required)
        String typeStr = getCellStringValue(row.getCell(2));
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                product.setProductType(Product.ProductType.valueOf(typeStr.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                product.setProductType(Product.ProductType.PERIPHERAL);
            }
        } else {
            product.setProductType(Product.ProductType.PERIPHERAL);
        }

        // HPP (required)
        BigDecimal hpp = getCellBigDecimalValue(row.getCell(3));
        product.setHpp(hpp != null ? hpp : BigDecimal.ZERO);

        // Margin
        Double margin = getCellNumericValue(row.getCell(4));
        product.setMarginPercent(margin != null ? margin : 10.0);

        // Selling Price (optional - calculated if empty)
        BigDecimal sellingPrice = getCellBigDecimalValue(row.getCell(5));
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) == 0) {
            // Calculate from HPP + margin
            sellingPrice = product.getHpp().multiply(
                    BigDecimal.ONE.add(BigDecimal.valueOf(product.getMarginPercent() / 100.0)));
        }
        product.setSellingPrice(sellingPrice);

        // Stock
        Integer stock = getCellIntValue(row.getCell(6));
        product.setStock(stock != null ? stock : 0);

        // Description
        String desc = getCellStringValue(row.getCell(7));
        product.setDescription(desc);

        // Defaults
        product.setActive(true);
        product.setCategoryId(1L);
        product.setBrandId(1L);

        return product;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }

    private Double getCellNumericValue(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal getCellBigDecimalValue(Cell cell) {
        Double val = getCellNumericValue(cell);
        return val != null ? BigDecimal.valueOf(val) : null;
    }

    private Integer getCellIntValue(Cell cell) {
        Double val = getCellNumericValue(cell);
        return val != null ? val.intValue() : null;
    }

    /**
     * Result class for import operation
     */
    public static class ImportResult {
        private final List<String> successList = new ArrayList<>();
        private final List<String> updatedList = new ArrayList<>();
        private final List<String> skippedList = new ArrayList<>();
        private final List<String> failedList = new ArrayList<>();

        public void addSuccess(String sku) {
            successList.add(sku);
        }

        public void addUpdated(String sku) {
            updatedList.add(sku);
        }

        public void addSkipped(String sku, String reason) {
            skippedList.add(sku + " - " + reason);
        }

        public void addFailed(int rowNum, String reason) {
            failedList.add("Baris " + rowNum + ": " + reason);
        }

        public int getSuccessCount() {
            return successList.size();
        }

        public int getUpdatedCount() {
            return updatedList.size();
        }

        public int getSkippedCount() {
            return skippedList.size();
        }

        public int getFailedCount() {
            return failedList.size();
        }

        public List<String> getSuccessList() {
            return successList;
        }

        public List<String> getUpdatedList() {
            return updatedList;
        }

        public List<String> getSkippedList() {
            return skippedList;
        }

        public List<String> getFailedList() {
            return failedList;
        }

        public String getSummary() {
            return String.format(
                    "Produk baru: %d\nStok/produk diupdate: %d\nDilewati: %d\nGagal: %d",
                    successList.size(), updatedList.size(), skippedList.size(), failedList.size());
        }
    }
}


