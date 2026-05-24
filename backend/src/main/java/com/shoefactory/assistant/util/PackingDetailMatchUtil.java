package com.shoefactory.assistant.util;

import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecordDetail;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackingDetailMatchUtil {

    private static final Pattern CARTON_PATTERN = Pattern.compile("^(.*?)(\\d+)(.*?)$");

    private PackingDetailMatchUtil() {
    }

    public static boolean isMatchingPackingDetail(OrderRecordDetail detail, OrderPackingDetail packingDetail) {
        return isPackingRangeInsideOrderRange(detail, packingDetail);
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
        return new ParsedCarton(
                normalizeMatchText(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
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
