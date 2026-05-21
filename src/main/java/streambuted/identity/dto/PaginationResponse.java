package streambuted.identity.dto;

/**
 * Shared offset-based pagination metadata.
 */
public record PaginationResponse(
    int limit,
    int offset,
    long total
) {}
