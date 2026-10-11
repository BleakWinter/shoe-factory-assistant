package com.shoefactory.assistant.util;

import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import java.math.BigInteger;
import java.util.*;

public final class DevelopmentNoOptionBuilder {
    private DevelopmentNoOptionBuilder() {}

    public static List<DevelopmentNoOptionResponse> build(Collection<String> styles) {
        List<DevelopmentNoOptionResponse> options = new ArrayList<>();
        styles.stream().filter(style->style!=null && !style.isBlank()).distinct().sorted().forEach(style->{
            // 与原来的三级款号选择一致；叶子保留完整款号，避免不同前缀串款。
            List<String> parts = Arrays.asList(style.split("-"));
            parts = parts.subList(Math.max(0,parts.size()-3),parts.size());
            List<DevelopmentNoOptionResponse> nodes = options;
            for (int i=0;i<parts.size();i++) {
                String part = parts.get(i);
                if (i==parts.size()-1) {
                    String label = part;
                    for (var node:nodes) {
                        if (node.getChildren()==null && (node.getValue().equals(part) || node.getValue().endsWith("-"+part))) {
                            node.setLabel(node.getValue());
                            label = style;
                        }
                    }
                    nodes.add(new DevelopmentNoOptionResponse(style,label,null));
                } else {
                    String value = String.join("-",parts.subList(0,i+1));
                    var node = nodes.stream().filter(option->value.equals(option.getValue()) && option.getChildren()!=null).findFirst().orElse(null);
                    if (node==null) {
                        node = new DevelopmentNoOptionResponse(value,part,new ArrayList<>());
                        nodes.add(node);
                    }
                    nodes = node.getChildren();
                }
            }
        });
        sort(options);
        return options;
    }

    private static void sort(List<DevelopmentNoOptionResponse> options) {
        options.sort((left,right)->{
            if (left.getLabel().matches("\\d+") && right.getLabel().matches("\\d+")) {
                int comparison = new BigInteger(left.getLabel()).compareTo(new BigInteger(right.getLabel()));
                if (comparison!=0) return comparison;
            }
            return left.getLabel().compareToIgnoreCase(right.getLabel());
        });
        options.stream().filter(option->option.getChildren()!=null).forEach(option->sort(option.getChildren()));
    }
}
