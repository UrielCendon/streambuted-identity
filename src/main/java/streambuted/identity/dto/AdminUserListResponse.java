package streambuted.identity.dto;

import java.util.List;

/**
 * Paginated administrative account list.
 */
public record AdminUserListResponse(
    List<AdminUserResponse> data,
    PaginationResponse pagination
) {}
