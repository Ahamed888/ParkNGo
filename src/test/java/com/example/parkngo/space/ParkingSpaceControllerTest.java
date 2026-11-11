package com.example.parkngo.space;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityService;
import com.example.parkngo.staff.StaffFacilityAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingSpaceControllerTest {

    @Mock
    private ParkingSpaceService spaceService;

    @Mock
    private ParkingFacilityService facilityService;

    @Mock
    private StaffFacilityAccessService staffFacilityAccess;

    @InjectMocks
    private ParkingSpaceController controller;

    private ParkingFacility facility1;
    private ParkingSpace spaceA01;
    private ParkingSpace spaceA02;
    private ParkingSpace spaceEV01;

    @BeforeEach
    void setUp() {
        facility1 = new ParkingFacility();
        facility1.setId(1L);
        facility1.setName("Colombo Central Multi-Storey");

        spaceA01 = new ParkingSpace();
        spaceA01.setId(101L);
        spaceA01.setSpaceNumber("A-01");
        spaceA01.setFacility(facility1);
        spaceA01.setFloor(1);
        spaceA01.setZone("ZONE-A");
        spaceA01.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        spaceA01.setParkingType("STANDARD");
        spaceA01.setHourlyRate(BigDecimal.valueOf(250.00));

        spaceA02 = new ParkingSpace();
        spaceA02.setId(102L);
        spaceA02.setSpaceNumber("A-02");
        spaceA02.setFacility(facility1);
        spaceA02.setFloor(1);
        spaceA02.setZone("ZONE-A");
        spaceA02.setStatus(ParkingSpace.ParkingSpaceStatus.OCCUPIED);
        spaceA02.setParkingType("STANDARD");
        spaceA02.setHourlyRate(BigDecimal.valueOf(250.00));

        spaceEV01 = new ParkingSpace();
        spaceEV01.setId(103L);
        spaceEV01.setSpaceNumber("EV-01");
        spaceEV01.setFacility(facility1);
        spaceEV01.setFloor(1);
        spaceEV01.setZone("ZONE-EV");
        spaceEV01.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        spaceEV01.setParkingType("ELECTRIC");
        spaceEV01.setHasEVCharging(true);
        spaceEV01.setHourlyRate(BigDecimal.valueOf(400.00));
    }

    @Test
    @DisplayName("viewParkingSpacesAdmin includes storedSpaces in model for dropdown autocomplete")
    void testViewParkingSpacesAdminIncludesStoredSpaces() {
        when(facilityService.getAllFacilities()).thenReturn(List.of(facility1));
        when(spaceService.getAllParkingSpaces()).thenReturn(List.of(spaceA01, spaceA02, spaceEV01));
        when(spaceService.filterSpaces(null, null, null, null, null, null))
                .thenReturn(List.of(spaceA01, spaceA02, spaceEV01));

        Model model = new ConcurrentModel();
        String view = controller.viewParkingSpacesAdmin(model, null, null, null, null, null, null);

        assertEquals("admin/parking-spaces", view);
        assertTrue(model.containsAttribute("storedSpaces"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stored = (List<Map<String, Object>>) model.getAttribute("storedSpaces");
        assertNotNull(stored);
        assertEquals(3, stored.size());
        assertEquals("A-01", stored.get(0).get("spaceNumber"));
        assertEquals("Colombo Central Multi-Storey", stored.get(0).get("facilityName"));
    }

    @Test
    @DisplayName("suggestSpaceCodes returns matching stored spaces for query")
    void testSuggestSpaceCodesReturnsMatches() {
        when(spaceService.getAllParkingSpaces()).thenReturn(List.of(spaceA01, spaceA02, spaceEV01));

        List<Map<String, Object>> suggestions = controller.suggestSpaceCodes("A", null);

        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        assertEquals("A-01", suggestions.get(0).get("spaceNumber"));
        assertEquals("A-02", suggestions.get(1).get("spaceNumber"));
    }

    @Test
    @DisplayName("suggestSpaceCodes returns empty list for empty or blank query")
    void testSuggestSpaceCodesEmptyQuery() {
        List<Map<String, Object>> suggestions = controller.suggestSpaceCodes("", null);
        assertTrue(suggestions.isEmpty());

        suggestions = controller.suggestSpaceCodes(null, null);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    @DisplayName("suggestSpaceCodes filters by facility when facilityId is provided")
    void testSuggestSpaceCodesFacilityFilter() {
        when(spaceService.getFacilityParkingSpaces(1L)).thenReturn(List.of(spaceEV01));

        List<Map<String, Object>> suggestions = controller.suggestSpaceCodes("EV", 1L);

        assertNotNull(suggestions);
        assertEquals(1, suggestions.size());
        assertEquals("EV-01", suggestions.get(0).get("spaceNumber"));
        assertEquals("Colombo Central Multi-Storey", suggestions.get(0).get("facilityName"));
    }
}
