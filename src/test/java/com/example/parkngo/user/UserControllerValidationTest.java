package com.example.parkngo.user;

import com.example.parkngo.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerValidationTest {
    @Test
    void failedAdminEditPreservesSubmittedValuesAndFieldErrors() throws Exception {
        UserService userService = mock(UserService.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.findAll()).thenReturn(List.of());
        UserController controller = new UserController(userService, roleRepository, mock(SecurityUtil.class));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.editUser(7L, "!", "edited@example.com", "0771234567",
                "STAFF", User.UserStatus.ACTIVE, "08:00 - 16:00", "", 12L, model);

        assertEquals("admin/user-form", view);
        User submitted = (User) model.getAttribute("user");
        assertNotNull(submitted);
        assertEquals("!", submitted.getFullName());
        assertEquals("edited@example.com", submitted.getEmail());
        assertEquals("STAFF", model.getAttribute("submittedRoleName"));
        assertTrue(((Map<?, ?>) model.getAttribute("fieldErrors")).containsKey("fullName"));
        verify(userService, never()).adminUpdateUser(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any());
    }
}
