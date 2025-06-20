package ca.bazlur.eventsourcing.projections;

/**
 * Represents pagination parameters for querying projections.
 * Page numbers are 0-based (first page is 0).
 */
public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be greater than zero");
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public static PageRequest first(int size) {
        return new PageRequest(0, size);
    }

    public PageRequest next() {
        return new PageRequest(page + 1, size);
    }

    public int getOffset() {
        return page * size;
    }
}