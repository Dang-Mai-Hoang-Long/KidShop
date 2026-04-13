package com.example.demo.service;

import com.example.demo.entity.CategoryEntity;
import com.example.demo.entity.OrderEntity;
import com.example.demo.entity.OrderItemEntity;
import com.example.demo.entity.ProductEntity;
import com.example.demo.repository.OrderRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private static final DateTimeFormatter DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter YEAR_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy");

    private final OrderRepository orderRepository;

    public StatisticsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public StatisticsDashboard buildDashboard(String granularityInput,
                                              LocalDate startDateInput,
                                              LocalDate endDateInput,
                                              Integer topLimitInput) {
        Granularity granularity = Granularity.from(granularityInput);
        int topLimit = normalizeTopLimit(topLimitInput);

        LocalDate endDate = endDateInput == null ? LocalDate.now() : endDateInput;
        LocalDate startDate = resolveStartDate(granularity, startDateInput, endDate);

        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        LocalDateTime fromDateTime = startDate.atStartOfDay();
        LocalDateTime toDateTime = endDate.atTime(LocalTime.MAX);

        List<OrderEntity> deliveredOrders = orderRepository.findDeliveredOrdersWithItemsBetween(fromDateTime, toDateTime);

        TimelineSeries timelineSeries = buildTimelineSeries(deliveredOrders, granularity, startDate, endDate);
        List<TopProductStat> topProducts = buildTopProducts(deliveredOrders, topLimit);
        List<CategoryRevenueStat> topCategories = buildTopCategories(deliveredOrders, topLimit);
        MonthlyGrowthSeries monthlyGrowthSeries = buildMonthlyGrowthSeries(deliveredOrders, startDate, endDate);
        MonthlyGrowthSummary monthlyGrowthSummary = buildMonthlyGrowthSummary(monthlyGrowthSeries.rows);

        BigDecimal totalRevenue = deliveredOrders.stream()
                .map(OrderEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long deliveredOrderCount = deliveredOrders.size();
        BigDecimal averageOrderValue = deliveredOrderCount == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(deliveredOrderCount), 0, RoundingMode.HALF_UP);

        PeriodTopSummary dayTopSummary = buildPeriodSummary(
                "Top san pham trong ngay",
                endDate,
                endDate,
                3
        );

        LocalDate monthStart = endDate.withDayOfMonth(1);
        LocalDate monthEnd = YearMonth.from(endDate).atEndOfMonth();
        PeriodTopSummary monthTopSummary = buildPeriodSummary(
                "Top san pham trong thang",
                monthStart,
                monthEnd,
                3
        );

        LocalDate yearStart = endDate.withDayOfYear(1);
        LocalDate yearEnd = endDate.withDayOfYear(endDate.lengthOfYear());
        PeriodTopSummary yearTopSummary = buildPeriodSummary(
                "Top san pham trong nam",
                yearStart,
                yearEnd,
                3
        );

        return new StatisticsDashboard(
                granularity.name(),
                startDate,
                endDate,
                topLimit,
                timelineSeries.labels,
                timelineSeries.values,
                totalRevenue,
                formatCurrency(totalRevenue),
                deliveredOrderCount,
                formatCurrency(averageOrderValue),
                topProducts,
                topCategories,
                dayTopSummary,
                monthTopSummary,
                yearTopSummary,
                monthlyGrowthSeries.labels,
                monthlyGrowthSeries.revenueValues,
                monthlyGrowthSeries.growthPercentValues,
                monthlyGrowthSeries.rows,
                monthlyGrowthSummary
        );
    }

    public byte[] exportExcel(StatisticsDashboard dashboard) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            createOverviewSheet(workbook, dashboard);
            createTimelineSheet(workbook, dashboard);
            createTopProductsSheet(workbook, "TopProducts", dashboard.getTopProducts());
            createTopCategoriesSheet(workbook, dashboard);
            createTopSnapshotsSheet(workbook, dashboard);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Khong the tao file Excel thong ke.", exception);
        }
    }

    public byte[] exportPdf(StatisticsDashboard dashboard) {
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10);

            document.add(new Paragraph("Revenue Statistics Report", titleFont));
            document.add(new Paragraph("Range: " + dashboard.getStartDate().format(DATE_LABEL_FORMATTER) + " - " + dashboard.getEndDate().format(DATE_LABEL_FORMATTER), normalFont));
            document.add(new Paragraph("Granularity: " + dashboard.getGranularity(), normalFont));
            document.add(new Paragraph("Total revenue: " + dashboard.getTotalRevenueFormatted(), normalFont));
            document.add(new Paragraph("Delivered orders: " + dashboard.getDeliveredOrderCount(), normalFont));
            document.add(new Paragraph("Average order value: " + dashboard.getAverageOrderValueFormatted(), normalFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Revenue timeline", sectionFont));
            document.add(buildPdfTable(
                    List.of("Period", "Revenue"),
                    buildTimelineRows(dashboard)
            ));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Top products (selected range)", sectionFont));
            document.add(buildPdfTable(
                    List.of("Rank", "Product", "Quantity", "Revenue"),
                    buildTopProductRows(dashboard.getTopProducts())
            ));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Top categories (selected range)", sectionFont));
            document.add(buildPdfTable(
                    List.of("Rank", "Category", "Revenue"),
                    buildTopCategoryRows(dashboard.getTopCategories())
            ));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Top products by Day / Month / Year", sectionFont));
            document.add(new Paragraph("Day: " + dashboard.getDayTopSummary().getPeriodLabel(), normalFont));
            document.add(buildPdfTable(
                    List.of("Rank", "Product", "Quantity", "Revenue"),
                    buildTopProductRows(dashboard.getDayTopSummary().getTopProducts())
            ));
            document.add(new Paragraph("Month: " + dashboard.getMonthTopSummary().getPeriodLabel(), normalFont));
            document.add(buildPdfTable(
                    List.of("Rank", "Product", "Quantity", "Revenue"),
                    buildTopProductRows(dashboard.getMonthTopSummary().getTopProducts())
            ));
            document.add(new Paragraph("Year: " + dashboard.getYearTopSummary().getPeriodLabel(), normalFont));
            document.add(buildPdfTable(
                    List.of("Rank", "Product", "Quantity", "Revenue"),
                    buildTopProductRows(dashboard.getYearTopSummary().getTopProducts())
            ));

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Khong the tao file PDF thong ke.", exception);
        }
    }

    private void createOverviewSheet(XSSFWorkbook workbook, StatisticsDashboard dashboard) {
        Sheet sheet = workbook.createSheet("Overview");
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.createCell(0).setCellValue("Revenue Statistics Overview");

        rowIndex = writeLabelValueRow(sheet, rowIndex, "Granularity", dashboard.getGranularity());
        rowIndex = writeLabelValueRow(sheet, rowIndex, "Start date", dashboard.getStartDate().format(DATE_LABEL_FORMATTER));
        rowIndex = writeLabelValueRow(sheet, rowIndex, "End date", dashboard.getEndDate().format(DATE_LABEL_FORMATTER));
        rowIndex = writeLabelValueRow(sheet, rowIndex, "Total revenue", dashboard.getTotalRevenue().doubleValue());
        rowIndex = writeLabelValueRow(sheet, rowIndex, "Delivered orders", dashboard.getDeliveredOrderCount());
        writeLabelValueRow(sheet, rowIndex, "Average order value", dashboard.getAverageOrderValueFormatted());

        autoSizeColumns(sheet, 3);
    }

    private void createTimelineSheet(XSSFWorkbook workbook, StatisticsDashboard dashboard) {
        Sheet sheet = workbook.createSheet("RevenueTimeline");
        int rowIndex = 0;

        Row headerRow = sheet.createRow(rowIndex++);
        headerRow.createCell(0).setCellValue("Period");
        headerRow.createCell(1).setCellValue("Revenue");

        List<String> labels = dashboard.getRevenueLabels();
        List<BigDecimal> values = dashboard.getRevenueValues();

        for (int i = 0; i < labels.size(); i++) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(labels.get(i));
            row.createCell(1).setCellValue(values.get(i).doubleValue());
        }

        autoSizeColumns(sheet, 2);
    }

    private void createTopProductsSheet(XSSFWorkbook workbook, String sheetName, List<TopProductStat> topProducts) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIndex = 0;

        Row headerRow = sheet.createRow(rowIndex++);
        headerRow.createCell(0).setCellValue("Rank");
        headerRow.createCell(1).setCellValue("Product");
        headerRow.createCell(2).setCellValue("Quantity");
        headerRow.createCell(3).setCellValue("Revenue");

        for (TopProductStat topProduct : topProducts) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(topProduct.getRank());
            row.createCell(1).setCellValue(topProduct.getName());
            row.createCell(2).setCellValue(topProduct.getQuantity());
            row.createCell(3).setCellValue(topProduct.getRevenue().doubleValue());
        }

        autoSizeColumns(sheet, 4);
    }

    private void createTopCategoriesSheet(XSSFWorkbook workbook, StatisticsDashboard dashboard) {
        Sheet sheet = workbook.createSheet("TopCategories");
        int rowIndex = 0;

        Row headerRow = sheet.createRow(rowIndex++);
        headerRow.createCell(0).setCellValue("Rank");
        headerRow.createCell(1).setCellValue("Category");
        headerRow.createCell(2).setCellValue("Revenue");

        for (CategoryRevenueStat topCategory : dashboard.getTopCategories()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(topCategory.getRank());
            row.createCell(1).setCellValue(topCategory.getCategoryName());
            row.createCell(2).setCellValue(topCategory.getRevenue().doubleValue());
        }

        autoSizeColumns(sheet, 3);
    }

    private void createTopSnapshotsSheet(XSSFWorkbook workbook, StatisticsDashboard dashboard) {
        Sheet sheet = workbook.createSheet("TopByDayMonthYear");
        int rowIndex = 0;

        rowIndex = writePeriodSummaryBlock(sheet, rowIndex, dashboard.getDayTopSummary());
        rowIndex++;
        rowIndex = writePeriodSummaryBlock(sheet, rowIndex, dashboard.getMonthTopSummary());
        rowIndex++;
        writePeriodSummaryBlock(sheet, rowIndex, dashboard.getYearTopSummary());

        autoSizeColumns(sheet, 5);
    }

    private int writePeriodSummaryBlock(Sheet sheet, int rowIndex, PeriodTopSummary summary) {
        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.createCell(0).setCellValue(summary.getTitle() + " - " + summary.getPeriodLabel());

        Row headerRow = sheet.createRow(rowIndex++);
        headerRow.createCell(0).setCellValue("Rank");
        headerRow.createCell(1).setCellValue("Product");
        headerRow.createCell(2).setCellValue("Quantity");
        headerRow.createCell(3).setCellValue("Revenue");

        for (TopProductStat topProduct : summary.getTopProducts()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(topProduct.getRank());
            row.createCell(1).setCellValue(topProduct.getName());
            row.createCell(2).setCellValue(topProduct.getQuantity());
            row.createCell(3).setCellValue(topProduct.getRevenue().doubleValue());
        }

        return rowIndex;
    }

    private int writeLabelValueRow(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIndex;
    }

    private int writeLabelValueRow(Sheet sheet, int rowIndex, String label, long value) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIndex;
    }

    private int writeLabelValueRow(Sheet sheet, int rowIndex, String label, double value) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIndex;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private PdfPTable buildPdfTable(List<String> headers, List<List<String>> rows) {
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100f);

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header));
            cell.setPadding(6f);
            table.addCell(cell);
        }

        for (List<String> row : rows) {
            for (String value : row) {
                PdfPCell cell = new PdfPCell(new Phrase(value));
                cell.setPadding(5f);
                table.addCell(cell);
            }
        }

        return table;
    }

    private List<List<String>> buildTimelineRows(StatisticsDashboard dashboard) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < dashboard.getRevenueLabels().size(); i++) {
            rows.add(List.of(
                    dashboard.getRevenueLabels().get(i),
                    formatCurrency(dashboard.getRevenueValues().get(i))
            ));
        }
        return rows;
    }

    private List<List<String>> buildTopProductRows(List<TopProductStat> topProducts) {
        List<List<String>> rows = new ArrayList<>();
        if (topProducts.isEmpty()) {
            rows.add(List.of("-", "No data", "0", "0"));
            return rows;
        }

        for (TopProductStat topProduct : topProducts) {
            rows.add(List.of(
                    String.valueOf(topProduct.getRank()),
                    topProduct.getName(),
                    String.valueOf(topProduct.getQuantity()),
                    topProduct.getRevenueFormatted()
            ));
        }
        return rows;
    }

    private List<List<String>> buildTopCategoryRows(List<CategoryRevenueStat> topCategories) {
        List<List<String>> rows = new ArrayList<>();
        if (topCategories.isEmpty()) {
            rows.add(List.of("-", "No data", "0"));
            return rows;
        }

        for (CategoryRevenueStat topCategory : topCategories) {
            rows.add(List.of(
                    String.valueOf(topCategory.getRank()),
                    topCategory.getCategoryName(),
                    topCategory.getRevenueFormatted()
            ));
        }
        return rows;
    }

    private PeriodTopSummary buildPeriodSummary(String title, LocalDate startDate, LocalDate endDate, int topLimit) {
        List<OrderEntity> orders = orderRepository.findDeliveredOrdersWithItemsBetween(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));
        List<TopProductStat> topProducts = buildTopProducts(orders, topLimit);

        List<String> labels = topProducts.stream().map(TopProductStat::getName).toList();
        List<BigDecimal> values = topProducts.stream().map(TopProductStat::getRevenue).toList();

        int totalQuantity = topProducts.stream().mapToInt(TopProductStat::getQuantity).sum();
        BigDecimal totalRevenue = topProducts.stream().map(TopProductStat::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);

        String periodLabel = startDate.equals(endDate)
                ? startDate.format(DATE_LABEL_FORMATTER)
                : startDate.format(DATE_LABEL_FORMATTER) + " - " + endDate.format(DATE_LABEL_FORMATTER);

        return new PeriodTopSummary(
                title,
                periodLabel,
                topProducts,
                labels,
                values,
                totalQuantity,
                totalRevenue,
                formatCurrency(totalRevenue)
        );
    }

    private TimelineSeries buildTimelineSeries(List<OrderEntity> orders,
                                               Granularity granularity,
                                               LocalDate startDate,
                                               LocalDate endDate) {
        LinkedHashMap<LocalDate, BigDecimal> timelineBuckets = initTimelineBuckets(granularity, startDate, endDate);

        for (OrderEntity order : orders) {
            LocalDate bucketKey = resolveBucketKey(order.getCreatedAt(), granularity);
            timelineBuckets.computeIfPresent(bucketKey, (key, value) -> value.add(safeAmount(order.getTotalAmount())));
        }

        List<String> labels = timelineBuckets.keySet().stream()
                .map(date -> formatPeriodLabel(date, granularity))
                .toList();

        List<BigDecimal> values = new ArrayList<>(timelineBuckets.values());

        return new TimelineSeries(labels, values);
    }

    private MonthlyGrowthSeries buildMonthlyGrowthSeries(List<OrderEntity> orders,
                                                         LocalDate startDate,
                                                         LocalDate endDate) {
        LinkedHashMap<YearMonth, BigDecimal> revenueBuckets = initMonthlyRevenueBuckets(startDate, endDate);

        for (OrderEntity order : orders) {
            YearMonth monthKey = YearMonth.from(order.getCreatedAt().toLocalDate());
            revenueBuckets.computeIfPresent(monthKey, (key, value) -> value.add(safeAmount(order.getTotalAmount())));
        }

        List<String> labels = new ArrayList<>();
        List<BigDecimal> revenueValues = new ArrayList<>();
        List<BigDecimal> growthPercentValues = new ArrayList<>();
        List<MonthlyGrowthStat> rows = new ArrayList<>();

        BigDecimal previousRevenue = null;
        for (Map.Entry<YearMonth, BigDecimal> entry : revenueBuckets.entrySet()) {
            String monthLabel = entry.getKey().format(MONTH_LABEL_FORMATTER);
            BigDecimal revenue = entry.getValue();
            BigDecimal growthPercent = resolveGrowthPercent(previousRevenue, revenue);
            boolean hasPreviousMonth = previousRevenue != null;

            labels.add(monthLabel);
            revenueValues.add(revenue);
            growthPercentValues.add(growthPercent);

            BigDecimal previousValue = hasPreviousMonth ? previousRevenue : BigDecimal.ZERO;
            String previousValueFormatted = hasPreviousMonth ? formatCurrency(previousValue) : "--";
            String growthPercentFormatted = hasPreviousMonth ? formatGrowthPercent(growthPercent) : "--";
            rows.add(new MonthlyGrowthStat(
                    monthLabel,
                    revenue,
                    formatCurrency(revenue),
                    previousValue,
                    previousValueFormatted,
                    growthPercent,
                    growthPercentFormatted,
                    hasPreviousMonth
            ));

            previousRevenue = revenue;
        }

        return new MonthlyGrowthSeries(labels, revenueValues, growthPercentValues, rows);
    }

    private LinkedHashMap<YearMonth, BigDecimal> initMonthlyRevenueBuckets(LocalDate startDate, LocalDate endDate) {
        LinkedHashMap<YearMonth, BigDecimal> buckets = new LinkedHashMap<>();

        YearMonth cursor = YearMonth.from(startDate.withDayOfMonth(1));
        YearMonth endCursor = YearMonth.from(endDate.withDayOfMonth(1));
        while (!cursor.isAfter(endCursor)) {
            buckets.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusMonths(1);
        }

        return buckets;
    }

    private MonthlyGrowthSummary buildMonthlyGrowthSummary(List<MonthlyGrowthStat> rows) {
        if (rows == null || rows.isEmpty()) {
            BigDecimal zero = BigDecimal.ZERO;
            return new MonthlyGrowthSummary(
                    "--",
                    zero,
                    formatCurrency(zero),
                    zero,
                    "--",
                    zero,
                    "--",
                    "text-muted",
                    false
            );
        }

        MonthlyGrowthStat latest = rows.get(rows.size() - 1);
        BigDecimal growth = latest.getGrowthPercent();
        String growthClass;
        if (!latest.isHasPreviousMonth()) {
            growthClass = "text-muted";
        } else {
            growthClass = growth.signum() > 0 ? "text-success" : growth.signum() < 0 ? "text-danger" : "text-muted";
        }

        return new MonthlyGrowthSummary(
                latest.getMonthLabel(),
                latest.getRevenue(),
                latest.getRevenueFormatted(),
                latest.getPreviousRevenue(),
                latest.getPreviousRevenueFormatted(),
                growth,
                latest.getGrowthPercentFormatted(),
                growthClass,
                latest.isHasPreviousMonth()
        );
    }

    private BigDecimal resolveGrowthPercent(BigDecimal previousRevenue, BigDecimal currentRevenue) {
        if (previousRevenue == null) {
            return BigDecimal.ZERO;
        }

        if (previousRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return currentRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.valueOf(100)
                    : BigDecimal.ZERO;
        }

        return currentRevenue.subtract(previousRevenue)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousRevenue, 2, RoundingMode.HALF_UP);
    }

    private String formatGrowthPercent(BigDecimal growthPercent) {
        BigDecimal safeValue = growthPercent == null ? BigDecimal.ZERO : growthPercent.setScale(2, RoundingMode.HALF_UP);
        String prefix = safeValue.signum() > 0 ? "+" : "";
        return prefix + safeValue.toPlainString() + "%";
    }

    private LinkedHashMap<LocalDate, BigDecimal> initTimelineBuckets(Granularity granularity,
                                                                      LocalDate startDate,
                                                                      LocalDate endDate) {
        LinkedHashMap<LocalDate, BigDecimal> buckets = new LinkedHashMap<>();

        switch (granularity) {
            case MONTH -> {
                LocalDate cursor = startDate.withDayOfMonth(1);
                LocalDate endCursor = endDate.withDayOfMonth(1);
                while (!cursor.isAfter(endCursor)) {
                    buckets.put(cursor, BigDecimal.ZERO);
                    cursor = cursor.plusMonths(1);
                }
            }
            case YEAR -> {
                LocalDate cursor = startDate.withDayOfYear(1);
                LocalDate endCursor = endDate.withDayOfYear(1);
                while (!cursor.isAfter(endCursor)) {
                    buckets.put(cursor, BigDecimal.ZERO);
                    cursor = cursor.plusYears(1);
                }
            }
            case DAY -> {
                LocalDate cursor = startDate;
                while (!cursor.isAfter(endDate)) {
                    buckets.put(cursor, BigDecimal.ZERO);
                    cursor = cursor.plusDays(1);
                }
            }
        }

        return buckets;
    }

    private LocalDate resolveBucketKey(LocalDateTime dateTime, Granularity granularity) {
        LocalDate date = dateTime.toLocalDate();
        return switch (granularity) {
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
            case DAY -> date;
        };
    }

    private String formatPeriodLabel(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case MONTH -> date.format(MONTH_LABEL_FORMATTER);
            case YEAR -> date.format(YEAR_LABEL_FORMATTER);
            case DAY -> date.format(DATE_LABEL_FORMATTER);
        };
    }

    private List<TopProductStat> buildTopProducts(List<OrderEntity> orders, int topLimit) {
        Map<String, ProductAccumulator> accumulators = new LinkedHashMap<>();

        for (OrderEntity order : orders) {
            for (OrderItemEntity item : order.getItems()) {
                String key = resolveProductKey(item);
                ProductAccumulator accumulator = accumulators.computeIfAbsent(key, ignored -> new ProductAccumulator(resolveProductLabel(item)));
                accumulator.quantity += item.getQuantity();
                accumulator.revenue = accumulator.revenue.add(item.getSubtotal());
            }
        }

        List<ProductAccumulator> sorted = accumulators.values().stream()
                .sorted(Comparator
                        .comparing((ProductAccumulator value) -> value.revenue, Comparator.reverseOrder())
                        .thenComparing((ProductAccumulator value) -> value.quantity, Comparator.reverseOrder())
                        .thenComparing(value -> value.name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<TopProductStat> topProducts = new ArrayList<>();
        for (int i = 0; i < Math.min(topLimit, sorted.size()); i++) {
            ProductAccumulator item = sorted.get(i);
            topProducts.add(new TopProductStat(i + 1, item.name, item.quantity, item.revenue, formatCurrency(item.revenue)));
        }

        return topProducts;
    }

    private List<CategoryRevenueStat> buildTopCategories(List<OrderEntity> orders, int topLimit) {
        Map<String, BigDecimal> categoryRevenue = new LinkedHashMap<>();

        for (OrderEntity order : orders) {
            for (OrderItemEntity item : order.getItems()) {
                String categoryName = resolveCategoryName(item);
                BigDecimal subtotal = item.getSubtotal();
                categoryRevenue.put(categoryName, categoryRevenue.getOrDefault(categoryName, BigDecimal.ZERO).add(subtotal));
            }
        }

        List<Map.Entry<String, BigDecimal>> sorted = categoryRevenue.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        List<CategoryRevenueStat> categories = new ArrayList<>();
        for (int i = 0; i < Math.min(topLimit, sorted.size()); i++) {
            Map.Entry<String, BigDecimal> entry = sorted.get(i);
            categories.add(new CategoryRevenueStat(
                    i + 1,
                    entry.getKey(),
                    entry.getValue(),
                    formatCurrency(entry.getValue())
            ));
        }

        return categories;
    }

    private String resolveProductKey(OrderItemEntity item) {
        String productCode = item.getProductCode();
        if (productCode != null && !productCode.isBlank()) {
            return productCode.trim().toUpperCase(Locale.ROOT);
        }
        return resolveProductLabel(item).toUpperCase(Locale.ROOT);
    }

    private String resolveProductLabel(OrderItemEntity item) {
        String productName = item.getProductName();
        if (productName == null || productName.isBlank()) {
            return "San pham khong xac dinh";
        }
        return productName.trim();
    }

    private String resolveCategoryName(OrderItemEntity item) {
        ProductEntity product = item.getProduct();
        if (product == null) {
            return "Chua phan loai";
        }

        CategoryEntity category = product.getCategory();
        if (category == null || category.getName() == null || category.getName().isBlank()) {
            return "Chua phan loai";
        }

        return category.getName().trim();
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int normalizeTopLimit(Integer topLimitInput) {
        if (topLimitInput == null) {
            return 10;
        }
        return Math.max(3, Math.min(topLimitInput, 30));
    }

    private LocalDate resolveStartDate(Granularity granularity, LocalDate startDateInput, LocalDate endDate) {
        if (startDateInput != null) {
            return startDateInput;
        }

        return switch (granularity) {
            case MONTH -> endDate.minusMonths(11).withDayOfMonth(1);
            case YEAR -> endDate.minusYears(4).withDayOfYear(1);
            case DAY -> endDate.minusDays(29);
        };
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return String.format("%,.0f", value) + "đ";
    }

    private static class TimelineSeries {
        private final List<String> labels;
        private final List<BigDecimal> values;

        private TimelineSeries(List<String> labels, List<BigDecimal> values) {
            this.labels = labels;
            this.values = values;
        }
    }

    private static class MonthlyGrowthSeries {
        private final List<String> labels;
        private final List<BigDecimal> revenueValues;
        private final List<BigDecimal> growthPercentValues;
        private final List<MonthlyGrowthStat> rows;

        private MonthlyGrowthSeries(List<String> labels,
                                    List<BigDecimal> revenueValues,
                                    List<BigDecimal> growthPercentValues,
                                    List<MonthlyGrowthStat> rows) {
            this.labels = labels;
            this.revenueValues = revenueValues;
            this.growthPercentValues = growthPercentValues;
            this.rows = rows;
        }
    }

    private static class ProductAccumulator {
        private final String name;
        private int quantity;
        private BigDecimal revenue = BigDecimal.ZERO;

        private ProductAccumulator(String name) {
            this.name = name;
        }
    }

    public static class StatisticsDashboard {
        private final String granularity;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final int topLimit;
        private final List<String> revenueLabels;
        private final List<BigDecimal> revenueValues;
        private final BigDecimal totalRevenue;
        private final String totalRevenueFormatted;
        private final long deliveredOrderCount;
        private final String averageOrderValueFormatted;
        private final List<TopProductStat> topProducts;
        private final List<CategoryRevenueStat> topCategories;
        private final PeriodTopSummary dayTopSummary;
        private final PeriodTopSummary monthTopSummary;
        private final PeriodTopSummary yearTopSummary;
        private final List<String> monthlyGrowthLabels;
        private final List<BigDecimal> monthlyRevenueValues;
        private final List<BigDecimal> monthlyGrowthPercentValues;
        private final List<MonthlyGrowthStat> monthlyGrowthStats;
        private final MonthlyGrowthSummary monthlyGrowthSummary;

        public StatisticsDashboard(String granularity,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   int topLimit,
                                   List<String> revenueLabels,
                                   List<BigDecimal> revenueValues,
                                   BigDecimal totalRevenue,
                                   String totalRevenueFormatted,
                                   long deliveredOrderCount,
                                   String averageOrderValueFormatted,
                                   List<TopProductStat> topProducts,
                                   List<CategoryRevenueStat> topCategories,
                                   PeriodTopSummary dayTopSummary,
                                   PeriodTopSummary monthTopSummary,
                                   PeriodTopSummary yearTopSummary,
                                   List<String> monthlyGrowthLabels,
                                   List<BigDecimal> monthlyRevenueValues,
                                   List<BigDecimal> monthlyGrowthPercentValues,
                                   List<MonthlyGrowthStat> monthlyGrowthStats,
                                   MonthlyGrowthSummary monthlyGrowthSummary) {
            this.granularity = granularity;
            this.startDate = startDate;
            this.endDate = endDate;
            this.topLimit = topLimit;
            this.revenueLabels = revenueLabels;
            this.revenueValues = revenueValues;
            this.totalRevenue = totalRevenue;
            this.totalRevenueFormatted = totalRevenueFormatted;
            this.deliveredOrderCount = deliveredOrderCount;
            this.averageOrderValueFormatted = averageOrderValueFormatted;
            this.topProducts = topProducts;
            this.topCategories = topCategories;
            this.dayTopSummary = dayTopSummary;
            this.monthTopSummary = monthTopSummary;
            this.yearTopSummary = yearTopSummary;
            this.monthlyGrowthLabels = monthlyGrowthLabels;
            this.monthlyRevenueValues = monthlyRevenueValues;
            this.monthlyGrowthPercentValues = monthlyGrowthPercentValues;
            this.monthlyGrowthStats = monthlyGrowthStats;
            this.monthlyGrowthSummary = monthlyGrowthSummary;
        }

        public String getGranularity() {
            return granularity;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public int getTopLimit() {
            return topLimit;
        }

        public List<String> getRevenueLabels() {
            return revenueLabels;
        }

        public List<BigDecimal> getRevenueValues() {
            return revenueValues;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }

        public String getTotalRevenueFormatted() {
            return totalRevenueFormatted;
        }

        public long getDeliveredOrderCount() {
            return deliveredOrderCount;
        }

        public String getAverageOrderValueFormatted() {
            return averageOrderValueFormatted;
        }

        public List<TopProductStat> getTopProducts() {
            return topProducts;
        }

        public List<CategoryRevenueStat> getTopCategories() {
            return topCategories;
        }

        public PeriodTopSummary getDayTopSummary() {
            return dayTopSummary;
        }

        public PeriodTopSummary getMonthTopSummary() {
            return monthTopSummary;
        }

        public PeriodTopSummary getYearTopSummary() {
            return yearTopSummary;
        }

        public List<String> getMonthlyGrowthLabels() {
            return monthlyGrowthLabels;
        }

        public List<BigDecimal> getMonthlyRevenueValues() {
            return monthlyRevenueValues;
        }

        public List<BigDecimal> getMonthlyGrowthPercentValues() {
            return monthlyGrowthPercentValues;
        }

        public List<MonthlyGrowthStat> getMonthlyGrowthStats() {
            return monthlyGrowthStats;
        }

        public MonthlyGrowthSummary getMonthlyGrowthSummary() {
            return monthlyGrowthSummary;
        }
    }

    public static class TopProductStat {
        private final int rank;
        private final String name;
        private final int quantity;
        private final BigDecimal revenue;
        private final String revenueFormatted;

        public TopProductStat(int rank, String name, int quantity, BigDecimal revenue, String revenueFormatted) {
            this.rank = rank;
            this.name = name;
            this.quantity = quantity;
            this.revenue = revenue;
            this.revenueFormatted = revenueFormatted;
        }

        public int getRank() {
            return rank;
        }

        public String getName() {
            return name;
        }

        public int getQuantity() {
            return quantity;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public String getRevenueFormatted() {
            return revenueFormatted;
        }
    }

    public static class CategoryRevenueStat {
        private final int rank;
        private final String categoryName;
        private final BigDecimal revenue;
        private final String revenueFormatted;

        public CategoryRevenueStat(int rank, String categoryName, BigDecimal revenue, String revenueFormatted) {
            this.rank = rank;
            this.categoryName = categoryName;
            this.revenue = revenue;
            this.revenueFormatted = revenueFormatted;
        }

        public int getRank() {
            return rank;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public String getRevenueFormatted() {
            return revenueFormatted;
        }
    }

    public static class MonthlyGrowthStat {
        private final String monthLabel;
        private final BigDecimal revenue;
        private final String revenueFormatted;
        private final BigDecimal previousRevenue;
        private final String previousRevenueFormatted;
        private final BigDecimal growthPercent;
        private final String growthPercentFormatted;
        private final boolean hasPreviousMonth;

        public MonthlyGrowthStat(String monthLabel,
                                 BigDecimal revenue,
                                 String revenueFormatted,
                                 BigDecimal previousRevenue,
                                 String previousRevenueFormatted,
                                 BigDecimal growthPercent,
                                 String growthPercentFormatted,
                                 boolean hasPreviousMonth) {
            this.monthLabel = monthLabel;
            this.revenue = revenue;
            this.revenueFormatted = revenueFormatted;
            this.previousRevenue = previousRevenue;
            this.previousRevenueFormatted = previousRevenueFormatted;
            this.growthPercent = growthPercent;
            this.growthPercentFormatted = growthPercentFormatted;
            this.hasPreviousMonth = hasPreviousMonth;
        }

        public String getMonthLabel() {
            return monthLabel;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public String getRevenueFormatted() {
            return revenueFormatted;
        }

        public BigDecimal getPreviousRevenue() {
            return previousRevenue;
        }

        public String getPreviousRevenueFormatted() {
            return previousRevenueFormatted;
        }

        public BigDecimal getGrowthPercent() {
            return growthPercent;
        }

        public String getGrowthPercentFormatted() {
            return growthPercentFormatted;
        }

        public boolean isHasPreviousMonth() {
            return hasPreviousMonth;
        }

        public boolean isPositiveGrowth() {
            return hasPreviousMonth && growthPercent != null && growthPercent.signum() > 0;
        }

        public boolean isNegativeGrowth() {
            return hasPreviousMonth && growthPercent != null && growthPercent.signum() < 0;
        }
    }

    public static class MonthlyGrowthSummary {
        private final String latestMonthLabel;
        private final BigDecimal latestRevenue;
        private final String latestRevenueFormatted;
        private final BigDecimal previousRevenue;
        private final String previousRevenueFormatted;
        private final BigDecimal growthPercent;
        private final String growthPercentFormatted;
        private final String growthClass;
        private final boolean hasPreviousMonth;

        public MonthlyGrowthSummary(String latestMonthLabel,
                                    BigDecimal latestRevenue,
                                    String latestRevenueFormatted,
                                    BigDecimal previousRevenue,
                                    String previousRevenueFormatted,
                                    BigDecimal growthPercent,
                                    String growthPercentFormatted,
                                    String growthClass,
                                    boolean hasPreviousMonth) {
            this.latestMonthLabel = latestMonthLabel;
            this.latestRevenue = latestRevenue;
            this.latestRevenueFormatted = latestRevenueFormatted;
            this.previousRevenue = previousRevenue;
            this.previousRevenueFormatted = previousRevenueFormatted;
            this.growthPercent = growthPercent;
            this.growthPercentFormatted = growthPercentFormatted;
            this.growthClass = growthClass;
            this.hasPreviousMonth = hasPreviousMonth;
        }

        public String getLatestMonthLabel() {
            return latestMonthLabel;
        }

        public BigDecimal getLatestRevenue() {
            return latestRevenue;
        }

        public String getLatestRevenueFormatted() {
            return latestRevenueFormatted;
        }

        public BigDecimal getPreviousRevenue() {
            return previousRevenue;
        }

        public String getPreviousRevenueFormatted() {
            return previousRevenueFormatted;
        }

        public BigDecimal getGrowthPercent() {
            return growthPercent;
        }

        public String getGrowthPercentFormatted() {
            return growthPercentFormatted;
        }

        public String getGrowthClass() {
            return growthClass;
        }

        public boolean isHasPreviousMonth() {
            return hasPreviousMonth;
        }
    }

    public static class PeriodTopSummary {
        private final String title;
        private final String periodLabel;
        private final List<TopProductStat> topProducts;
        private final List<String> chartLabels;
        private final List<BigDecimal> chartValues;
        private final int totalQuantity;
        private final BigDecimal totalRevenue;
        private final String totalRevenueFormatted;

        public PeriodTopSummary(String title,
                                String periodLabel,
                                List<TopProductStat> topProducts,
                                List<String> chartLabels,
                                List<BigDecimal> chartValues,
                                int totalQuantity,
                                BigDecimal totalRevenue,
                                String totalRevenueFormatted) {
            this.title = title;
            this.periodLabel = periodLabel;
            this.topProducts = topProducts;
            this.chartLabels = chartLabels;
            this.chartValues = chartValues;
            this.totalQuantity = totalQuantity;
            this.totalRevenue = totalRevenue;
            this.totalRevenueFormatted = totalRevenueFormatted;
        }

        public String getTitle() {
            return title;
        }

        public String getPeriodLabel() {
            return periodLabel;
        }

        public List<TopProductStat> getTopProducts() {
            return topProducts;
        }

        public List<String> getChartLabels() {
            return chartLabels;
        }

        public List<BigDecimal> getChartValues() {
            return chartValues;
        }

        public int getTotalQuantity() {
            return totalQuantity;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }

        public String getTotalRevenueFormatted() {
            return totalRevenueFormatted;
        }
    }

    private enum Granularity {
        DAY,
        MONTH,
        YEAR;

        private static Granularity from(String input) {
            if (input == null || input.isBlank()) {
                return DAY;
            }

            try {
                return Granularity.valueOf(input.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DAY;
            }
        }
    }
}
