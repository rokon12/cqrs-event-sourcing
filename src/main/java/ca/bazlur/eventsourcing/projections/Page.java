package ca.bazlur.eventsourcing.projections;

import java.util.List;

/**
 * Represents a page of results with pagination metadata.
 * @param <T> The type of elements in the page
 */
public record Page<T>(
    List<T> content,
    PageRequest pageRequest,
    long totalElements,
    int totalPages
) {
    public Page {
        content = List.copyOf(content); // Make content immutable
    }

    public static <T> Page<T> of(List<T> content, PageRequest pageRequest, long totalElements) {
        int totalPages = calculateTotalPages(pageRequest.size(), totalElements);
        return new Page<>(content, pageRequest, totalElements, totalPages);
    }

    private static int calculateTotalPages(int pageSize, long totalElements) {
        return pageSize > 0 ? (int) Math.ceil((double) totalElements / (double) pageSize) : 0;
    }

    public boolean hasNext() {
        return pageRequest.page() < totalPages - 1;
    }

    public boolean hasPrevious() {
        return pageRequest.page() > 0;
    }

    public boolean isFirst() {
        return !hasPrevious();
    }

    public boolean isLast() {
        return !hasNext();
    }
}