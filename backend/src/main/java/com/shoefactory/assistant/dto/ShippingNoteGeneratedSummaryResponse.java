package com.shoefactory.assistant.dto;

import java.util.Collection;
import java.util.List;

public class ShippingNoteGeneratedSummaryResponse {

    private List<Long> generatedDetailIds;
    private List<Long> fullyGeneratedOrderIds;

    public static ShippingNoteGeneratedSummaryResponse empty() {
        return of(List.of(), List.of());
    }

    public static ShippingNoteGeneratedSummaryResponse of(
            Collection<Long> generatedDetailIds,
            Collection<Long> fullyGeneratedOrderIds
    ) {
        ShippingNoteGeneratedSummaryResponse response = new ShippingNoteGeneratedSummaryResponse();
        response.setGeneratedDetailIds(toSortedList(generatedDetailIds));
        response.setFullyGeneratedOrderIds(toSortedList(fullyGeneratedOrderIds));
        return response;
    }

    private static List<Long> toSortedList(Collection<Long> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null)
                .distinct()
                .sorted()
                .toList();
    }

    public List<Long> getGeneratedDetailIds() {
        return generatedDetailIds == null ? List.of() : generatedDetailIds;
    }

    public void setGeneratedDetailIds(List<Long> generatedDetailIds) {
        this.generatedDetailIds = generatedDetailIds == null ? List.of() : generatedDetailIds;
    }

    public List<Long> getFullyGeneratedOrderIds() {
        return fullyGeneratedOrderIds == null ? List.of() : fullyGeneratedOrderIds;
    }

    public void setFullyGeneratedOrderIds(List<Long> fullyGeneratedOrderIds) {
        this.fullyGeneratedOrderIds = fullyGeneratedOrderIds == null ? List.of() : fullyGeneratedOrderIds;
    }
}
