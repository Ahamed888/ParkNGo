package com.example.parkngo.facility;

import com.example.parkngo.dashboard.DashboardService;
import com.example.parkngo.dashboard.dto.FacilityActivityRow;
import com.example.parkngo.dashboard.dto.FloorSummary;
import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.discount.PackageService;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacilityControllerTest {

    @Mock
    private ParkingFacilityService facilityService;

    @Mock
    private FacilityServiceService facilityServiceService;

    @Mock
    private PackageService packageService;

    @Mock
    private DiscountService discountService;

    @Mock
    private ParkingSpaceRepository parkingSpaceRepository;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private FacilityController facilityController;

    private ParkingFacility sampleFacility;

    @BeforeEach
    void setUp() {
        sampleFacility = new ParkingFacility();
        sampleFacility.setId(1L);
        sampleFacility.setName("City Centre Parking");
        sampleFacility.setAddress("Colombo 03");
        sampleFacility.setContactNumber("011-2345678");
        sampleFacility.setOpeningTime(LocalTime.of(6, 0));
        sampleFacility.setClosingTime(LocalTime.of(23, 0));
        sampleFacility.setTotalCapacity(100);
        sampleFacility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
    }

    @Test
    @DisplayName("viewFacilityDetails populates floorSummaries and recentActivity into model")
    void testViewFacilityDetailsPopulatesFloorSummariesAndRecentActivity() throws Exception {
        when(facilityService.getFacilityById(1L)).thenReturn(sampleFacility);
        when(facilityServiceService.getFacilityServices(1L)).thenReturn(List.of());

        ParkingSpace space1 = new ParkingSpace();
        space1.setId(10L);
        space1.setSpaceNumber("L1-01");
        space1.setFloor(1);
        space1.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);

        when(parkingSpaceRepository.findByFacilityId(1L)).thenReturn(List.of(space1));

        doAnswer(invocation -> {
            DashboardService.ModelLike modelLike = invocation.getArgument(0);
            modelLike.put("floorSummaries", List.of(new FloorSummary(1, 1, 0)));
            modelLike.put("recentActivity", List.of(
                    new FacilityActivityRow(LocalDateTime.now(), "WP-CAB-1234", "Check-in", "L1-01", 1)
            ));
            return null;
        }).when(dashboardService).enrichFacilityDetails(any(DashboardService.ModelLike.class), eq(sampleFacility), any(), any());

        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = facilityController.viewFacilityDetails(1L, model, redirectAttributes);

        assertEquals("admin/facility-details", view);
        assertEquals(sampleFacility, model.getAttribute("facility"));
        assertEquals("facility1@staff.parkngo.local", model.getAttribute("staffLoginEmail"));
        assertEquals("ParkNGo@Staff1", model.getAttribute("staffLoginPassword"));
        assertNotNull(model.getAttribute("floorSummaries"), "floorSummaries must not be null in model");
        assertNotNull(model.getAttribute("recentActivity"), "recentActivity must not be null in model");

        @SuppressWarnings("unchecked")
        List<FloorSummary> floors = (List<FloorSummary>) model.getAttribute("floorSummaries");
        assertEquals(1, floors.size());
        assertEquals(1, floors.get(0).floor());

        @SuppressWarnings("unchecked")
        List<FacilityActivityRow> activities = (List<FacilityActivityRow>) model.getAttribute("recentActivity");
        assertEquals(1, activities.size());
        assertEquals("WP-CAB-1234", activities.get(0).plate());

        verify(dashboardService).enrichFacilityDetails(any(DashboardService.ModelLike.class), eq(sampleFacility), any(), any());
    }

    @Test
    @DisplayName("viewFacilityDetails handles null dashboardService gracefully with empty lists")
    void testViewFacilityDetailsNullDashboardServiceFallback() throws Exception {
        FacilityController controllerWithoutDashboard = new FacilityController(
                facilityService, facilityServiceService, packageService, discountService, parkingSpaceRepository, null
        );

        when(facilityService.getFacilityById(1L)).thenReturn(sampleFacility);
        when(facilityServiceService.getFacilityServices(1L)).thenReturn(List.of());
        when(parkingSpaceRepository.findByFacilityId(1L)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controllerWithoutDashboard.viewFacilityDetails(1L, model, redirectAttributes);

        assertEquals("admin/facility-details", view);
        assertNotNull(model.getAttribute("floorSummaries"));
        assertNotNull(model.getAttribute("recentActivity"));
    }

    @Test
    @DisplayName("creating a complex reports its dedicated staff credentials")
    void createFacilityReportsStaffCredentials() throws Exception {
        when(facilityService.createFacility(sampleFacility)).thenReturn(sampleFacility);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = facilityController.addFacility(sampleFacility,
                new BeanPropertyBindingResult(sampleFacility, "facility"),
                redirectAttributes, new ExtendedModelMap(), null);

        assertEquals("redirect:/admin/facilities", view);
        String success = (String) redirectAttributes.getFlashAttributes().get("success");
        assertNotNull(success);
        assertEquals("City Centre Parking", redirectAttributes.getFlashAttributes().get("createdFacilityName"));
        assertEquals("facility1@staff.parkngo.local", redirectAttributes.getFlashAttributes().get("staffLoginEmail"));
        assertEquals("ParkNGo@Staff1", redirectAttributes.getFlashAttributes().get("staffLoginPassword"));
    }
}
