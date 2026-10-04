package com.casebrief.model;

/**
 * Generic wrapper for extracted data fields to maintain source page traceability.
 *
 * @param <T> The data type of the extracted field value.
 */
public class ExtractedField<T> {
    private T value;
    private Integer sourcePage;

    public ExtractedField() {
    }

    public ExtractedField(T value, Integer sourcePage) {
        this.value = value;
        this.sourcePage = sourcePage;
    }

    public static <T> ExtractedField<T> of(T value, Integer sourcePage) {
        return new ExtractedField<>(value, sourcePage);
    }

    public static <T> ExtractedField<T> unknown() {
        return new ExtractedField<>(null, null);
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }

    public boolean isPresent() {
        return value != null;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() + " (p. " + sourcePage + ")" : "N/A";
    }
}
