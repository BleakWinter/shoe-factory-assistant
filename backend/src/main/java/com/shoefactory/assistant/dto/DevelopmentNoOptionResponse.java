package com.shoefactory.assistant.dto;

import java.util.List;

public class DevelopmentNoOptionResponse {

    private String value;
    private String label;
    private List<DevelopmentNoOptionResponse> children;

    public DevelopmentNoOptionResponse() {
    }

    public DevelopmentNoOptionResponse(String value, String label, List<DevelopmentNoOptionResponse> children) {
        this.value = value;
        this.label = label;
        this.children = children;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<DevelopmentNoOptionResponse> getChildren() {
        return children;
    }

    public void setChildren(List<DevelopmentNoOptionResponse> children) {
        this.children = children;
    }
}
