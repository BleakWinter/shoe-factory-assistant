package com.shoefactory.assistant.dto;

import java.util.ArrayList;
import java.util.List;

public class OrderStatisticsResponse {

    private Integer totalPairs;
    private Integer styleCount;
    private Integer detailCount;
    private List<DevelopmentNoStatisticNode> developmentNoTree = new ArrayList<>();
    private List<DevelopmentNoStatisticNode> topDevelopmentNos = new ArrayList<>();

    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public Integer getStyleCount() { return styleCount; }
    public void setStyleCount(Integer styleCount) { this.styleCount = styleCount; }
    public Integer getDetailCount() { return detailCount; }
    public void setDetailCount(Integer detailCount) { this.detailCount = detailCount; }
    public List<DevelopmentNoStatisticNode> getDevelopmentNoTree() { return developmentNoTree; }
    public void setDevelopmentNoTree(List<DevelopmentNoStatisticNode> developmentNoTree) {
        this.developmentNoTree = developmentNoTree;
    }
    public List<DevelopmentNoStatisticNode> getTopDevelopmentNos() { return topDevelopmentNos; }
    public void setTopDevelopmentNos(List<DevelopmentNoStatisticNode> topDevelopmentNos) {
        this.topDevelopmentNos = topDevelopmentNos;
    }

    public static class DevelopmentNoStatisticNode {
        private String key;
        private String label;
        private String fullDevelopmentNo;
        private Integer level;
        private Integer pairCount;
        private Integer detailCount;
        private Integer styleCount;
        private List<DevelopmentNoStatisticNode> children = new ArrayList<>();

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getFullDevelopmentNo() { return fullDevelopmentNo; }
        public void setFullDevelopmentNo(String fullDevelopmentNo) { this.fullDevelopmentNo = fullDevelopmentNo; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public Integer getPairCount() { return pairCount; }
        public void setPairCount(Integer pairCount) { this.pairCount = pairCount; }
        public Integer getDetailCount() { return detailCount; }
        public void setDetailCount(Integer detailCount) { this.detailCount = detailCount; }
        public Integer getStyleCount() { return styleCount; }
        public void setStyleCount(Integer styleCount) { this.styleCount = styleCount; }
        public List<DevelopmentNoStatisticNode> getChildren() { return children; }
        public void setChildren(List<DevelopmentNoStatisticNode> children) { this.children = children; }
    }
}
