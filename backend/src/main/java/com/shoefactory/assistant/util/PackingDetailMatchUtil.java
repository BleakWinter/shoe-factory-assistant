package com.shoefactory.assistant.util;

import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecordDetail;

import java.util.Locale;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackingDetailMatchUtil {

    private static final Pattern CARTON_PATTERN = Pattern.compile("^(.*?)(\\d+)(.*?)$");

    private PackingDetailMatchUtil() {
    }

    public static boolean isMatchingPackingDetail(OrderRecordDetail detail, OrderPackingDetail packingDetail) {
        if (detail == null || packingDetail == null) {
            return false;
        }
        if (detail.getOrderId() != null && packingDetail.getOrderId() != null
                && !detail.getOrderId().equals(packingDetail.getOrderId())) {
            return false;
        }
        if (hasConflictingStyleNo(detail, packingDetail)) {
            return false;
        }
        return isPackingRangeInsideOrderRange(detail, packingDetail);
    }

    public static int getPackingCartonCount(OrderPackingDetail packingDetail) {
        if (packingDetail.getCartonCount() != null && packingDetail.getCartonCount() > 0) {
            return packingDetail.getCartonCount();
        }
        CartonRange range = getCartonRange(packingDetail.getCartonStart(), packingDetail.getCartonEnd());
        return range == null ? 1 : Math.addExact(Math.subtractExact(range.end(), range.start()), 1);
    }

    // 同订单同款可能分成多行，装箱行可跨越这些连续的箱号范围。
    public static boolean isMatchingPackingDetails(List<OrderRecordDetail> details, OrderPackingDetail packingDetail) {
        CartonRange packingRange = getCartonRange(packingDetail.getCartonStart(), packingDetail.getCartonEnd());
        if (packingRange == null) return false;
        List<CartonRange> ranges = details.stream()
                .filter(detail -> detail.getOrderId() != null && detail.getOrderId().equals(packingDetail.getOrderId()))
                .filter(detail -> !hasConflictingStyleNo(detail, packingDetail))
                .map(detail -> getCartonRange(detail.getCartonStart(), detail.getCartonEnd()))
                .filter(range -> range != null && range.prefix().equals(packingRange.prefix()) && range.suffix().equals(packingRange.suffix()))
                .sorted(Comparator.comparingInt(CartonRange::start)).toList();
        long next = packingRange.start();
        for (CartonRange range : ranges) {
            if (range.end() < next) continue;
            if (range.start() > next) return false;
            if (range.end() >= packingRange.end()) return true;
            next = (long) range.end() + 1;
        }
        return false;
    }

    private static boolean hasConflictingStyleNo(OrderRecordDetail detail, OrderPackingDetail packingDetail) {
        String developmentNo = normalizeMatchText(detail.getDevelopmentNo());
        String companyStyleNo = normalizeMatchText(packingDetail.getCompanyStyleNo());

        // 兼容缺少款号的旧数据；只要两边都有款号，就必须先确认是同一款。
        return !developmentNo.isEmpty()
                && !companyStyleNo.isEmpty()
                && !developmentNo.equals(companyStyleNo);
    }

    private static boolean isPackingRangeInsideOrderRange(OrderRecordDetail detail, OrderPackingDetail packingDetail) {
        CartonRange detailRange = getCartonRange(detail.getCartonStart(), detail.getCartonEnd());
        CartonRange packingRange = getCartonRange(packingDetail.getCartonStart(), packingDetail.getCartonEnd());
        if (detailRange == null || packingRange == null) {
            return false;
        }
        return detailRange.prefix().equals(packingRange.prefix())
                && detailRange.suffix().equals(packingRange.suffix())
                && detailRange.start() <= packingRange.start()
                && packingRange.end() <= detailRange.end();
    }

    private static CartonRange getCartonRange(String start, String end) {
        ParsedCarton startCarton = parseCarton(start);
        ParsedCarton endCarton = parseCarton(end);
        if (endCarton == null) {
            endCarton = startCarton;
        }
        if (startCarton == null || endCarton == null
                || !startCarton.prefix().equals(endCarton.prefix())
                || !startCarton.suffix().equals(endCarton.suffix())) {
            return null;
        }
        return new CartonRange(
                startCarton.prefix(),
                Math.min(startCarton.number(), endCarton.number()),
                Math.max(startCarton.number(), endCarton.number()),
                startCarton.suffix()
        );
    }

    private static ParsedCarton parseCarton(String value) {
        String text = value == null ? "" : value.trim();
        Matcher matcher = CARTON_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        int number;
        try {
            number = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ex) {
            return null;
        }
        return new ParsedCarton(
                normalizeMatchText(matcher.group(1)),
                number,
                normalizeMatchText(matcher.group(3))
        );
    }

    private static String normalizeMatchText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private record ParsedCarton(String prefix, int number, String suffix) {
    }

    private record CartonRange(String prefix, int start, int end, String suffix) {
    }
}
