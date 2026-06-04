package streambuted.identity.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import streambuted.identity.dto.AdminUserListResponse;
import streambuted.identity.dto.PaginationResponse;
import streambuted.identity.service.UserService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserController admin moderation tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin user list should accept searchTerm as a search alias")
    void listUsersForAdmin_acceptsSearchTermAlias() throws Exception {
        when(userService.listUsersForAdmin(10, 0, "artist"))
            .thenReturn(emptyAdminUserListResponse());

        mockMvc.perform(get("/api/v1/users/admin")
                .param("limit", "10")
                .param("offset", "0")
                .param("searchTerm", "artist"))
            .andExpect(status().isOk());

        verify(userService).listUsersForAdmin(10, 0, "artist");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin user list should prefer q when both search aliases are present")
    void listUsersForAdmin_prefersQOverSearchTermAlias() throws Exception {
        when(userService.listUsersForAdmin(10, 0, "artist"))
            .thenReturn(emptyAdminUserListResponse());

        mockMvc.perform(get("/api/v1/users/admin")
                .param("limit", "10")
                .param("offset", "0")
                .param("q", "artist")
                .param("searchTerm", "listener"))
            .andExpect(status().isOk());

        verify(userService).listUsersForAdmin(10, 0, "artist");
    }

    private AdminUserListResponse emptyAdminUserListResponse() {
        return new AdminUserListResponse(List.of(), new PaginationResponse(10, 0, 0));
    }
}
