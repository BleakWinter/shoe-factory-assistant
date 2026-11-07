package com.shoefactory.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.ShoeQuotationItem;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** 浏览器和 Excel 共用从原报价工作簿提取的版式，不读取或覆盖历史报价数据。 */
final class QuotationPrintLayout {
    private static final JsonNode TEMPLATE = load();
    private static final Pattern SOURCE = Pattern.compile("Excel 导入：([^；]+)；工作表：([^；]+)");
    private final JsonNode profile;
    QuotationPrintLayout(String note) {
        var match = SOURCE.matcher(note == null ? "" : note);
        var source = match.find() ? TEMPLATE.path("sourceProfiles").path(match.group(1)+"/"+match.group(2).trim()) : null;
        profile = source != null && !source.isMissingNode() ? source : TEMPLATE.path("base");
    }
    private static JsonNode load() {
        try (var input = QuotationPrintLayout.class.getResourceAsStream("/quotation-print-layout.json")) {
            if (input == null) throw new IllegalStateException("缺少报价打印版式");
            return new ObjectMapper().readTree(input);
        } catch (IOException ex) { throw new IllegalStateException("读取报价打印版式失败", ex); }
    }
    JsonNode template() { return TEMPLATE; }
    JsonNode property(String name) { return profile.has(name) ? profile.get(name) : TEMPLATE.path("base").path(name); }
    JsonNode style(int row, int col) {
        return TEMPLATE.path("styles").get(styleIndex(row, col));
    }
    int styleIndex(int row, int col) {
        String ref = "ABCDEFG".charAt(col)+String.valueOf(row);
        var value = profile.path("cellStyles").get(ref);
        if (value == null) value = TEMPLATE.path("base").path("cellStyles").get(ref);
        return value.asInt();
    }
    float rowHeight(int row) {
        var value = profile.path("rowHeights").get(String.valueOf(row));
        if (value == null) value = TEMPLATE.path("base").path("rowHeights").get(String.valueOf(row));
        return value == null ? 24 : (float)value.asDouble();
    }
    record Line(String label, ShoeQuotationItem item, int templateRow) {}
    List<Line> lines(List<ShoeQuotationItem> items, String category, int firstRow) {
        var entries = items.stream().filter(item -> category.equals(item.getCategory())).toList();
        int count = property("labels").path(category).size();
        var lines = new ArrayList<Line>();
        for (int slot = 0; slot < Math.max(count, entries.size()); slot++) {
            var item = slot < entries.size() ? entries.get(slot) : null;
            lines.add(new Line(item == null ? "" : item.getPartName(), item, firstRow+Math.min(slot,count-1)));
        }
        return lines;
    }
}
