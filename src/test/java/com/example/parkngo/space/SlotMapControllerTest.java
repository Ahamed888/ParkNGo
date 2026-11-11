package com.example.parkngo.space;

import com.example.parkngo.reservation.*;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import com.example.parkngo.staff.StaffFacilityAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlotMapControllerTest {
    @Test void cannotExcludeAnotherCustomersBooking() {
        var repo=mock(ReservationRepository.class);var security=mock(SecurityUtil.class);var service=mock(SlotMapService.class);
        var r=new Reservation();var customer=new User();customer.setId(9L);r.setCustomer(customer);
        when(repo.findById(7L)).thenReturn(Optional.of(r));when(security.getCurrentUserId()).thenReturn(10L);
        var ex=assertThrows(ResponseStatusException.class,()->new SlotMapController(service,repo,security,mock(StaffFacilityAccessService.class)).map(1L,null,null,7L));
        assertEquals(403,ex.getStatusCode().value());verifyNoInteractions(service);
    }
    @Test void invalidWindowRejectedBeforeQuery() {
        var service=mock(SlotMapService.class);var controller=new SlotMapController(service,mock(ReservationRepository.class),mock(SecurityUtil.class),mock(StaffFacilityAccessService.class));
        var now=LocalDateTime.now();assertThrows(ResponseStatusException.class,()->controller.map(1L,now,now,null));verifyNoInteractions(service);
    }
}
