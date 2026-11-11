package com.example.parkngo.space;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.reservation.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Render the real Thymeleaf pages without connecting to or seeding the application database. */
class RedesignTemplateTest {
    @Test void redesignedPagesRenderWithPopulatedAndEmptyData() throws Exception {
        var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setCharacterEncoding("UTF-8");resolver.setTemplateMode("HTML");
        var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        var servlet=new MockServletContext();
        var appContext = new org.springframework.web.context.support.GenericWebApplicationContext(servlet);
        appContext.refresh();
        servlet.setAttribute(org.springframework.web.context.WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appContext);
        var request=new MockHttpServletRequest(servlet);var response=new MockHttpServletResponse();
        var exchange=JakartaServletWebApplication.buildApplication(servlet).buildExchange(request,response);
        var f=new ParkingFacility();f.setId(1L);f.setName("City Centre Complex");f.setAddress("Colombo 03");f.setContactNumber("0112345678");f.setCity("Colombo");
        var model=new HashMap<String,Object>();model.put("facility",f);model.put("facilities",List.of(f));model.put("vehicleCount",2);
        for(String name:List.of("todayArrivals","todayDepartures","occupiedSpaces","availableSpaces","totalSpaces","facilityCount","occupancyPercentage","maintenanceSpaces","evSpaces","accessibleSpaces")) model.put(name,12);
        for(String name:List.of("recentArrivals","services","vehicles","spaces","availableServices","discounts"))model.put(name,List.of());
        var r=new Reservation();r.setArrivalTime(LocalDateTime.now().plusHours(1));r.setDepartureTime(LocalDateTime.now().plusHours(3));model.put("reservation",r);
        model.put("defaultArrival",r.getArrivalTime().toString());model.put("defaultDeparture",r.getDepartureTime().toString());model.put("mode","create");model.put("selectedFacilityId",1L);
        Path output=Path.of("target/ui-preview");Files.createDirectories(output);
        model.put("applicationName", "ParkNGo");model.put("sessionTimeout", "30m");model.put("turnoverBufferMinutes", 15);model.put("earlyCheckinMinutes", 15);model.put("currency", "LKR");
        model.put("reservations", List.of());model.put("filterStatus", null);model.put("filterFacilityId", null);model.put("filterDate", null);model.put("filterQ", null);
        var user = new com.example.parkngo.user.User(); user.setId(1L); user.setFullName("John Doe"); user.setEmail("john@example.com"); user.setPhone("0771234567");
        var role = new com.example.parkngo.user.Role(); role.setName("ADMIN"); user.setRole(role);
        model.put("user", user); model.put("currentUser", user);
        var pkg = new com.example.parkngo.discount.dto.PackageRequest();
        model.put("package", pkg);
        model.put("org.springframework.validation.BindingResult.package", new org.springframework.validation.BeanPropertyBindingResult(pkg, "package"));
        model.put("packages", List.of());
        var disc = new com.example.parkngo.discount.dto.DiscountRequest();
        model.put("discount", disc);
        model.put("org.springframework.validation.BindingResult.discount", new org.springframework.validation.BeanPropertyBindingResult(disc, "discount"));
        model.put("discounts", List.of());
        model.put("feedback", List.of());
        model.put("totalRevenue", java.math.BigDecimal.valueOf(15000));
        model.put("totalReservations", 100);
        model.put("completedReservations", 95);
        model.put("isEdit", false);
        var testPayment = new com.example.parkngo.payment.Payment();
        testPayment.setId(1L);
        testPayment.setAmount(java.math.BigDecimal.valueOf(2500));
        testPayment.setStatus(com.example.parkngo.payment.Payment.PaymentStatus.COMPLETED);
        testPayment.setPaymentMethod(com.example.parkngo.payment.Payment.PaymentMethod.CREDIT_CARD);
        testPayment.setTransactionCode("TXN-TEST-1234");
        testPayment.setUser(user);
        var serv = new com.example.parkngo.facility.FacilityService();
        model.put("service", serv);
        model.put("org.springframework.validation.BindingResult.service", new org.springframework.validation.BeanPropertyBindingResult(serv, "service"));
        model.put("totalServices", 4);
        model.put("availableCount", 4L);
        model.put("avgPrice", java.math.BigDecimal.valueOf(1200));
        request.setAttribute(org.springframework.web.servlet.support.RequestContext.WEB_APPLICATION_CONTEXT_ATTRIBUTE, appContext);
        var springRequestContext = new org.springframework.web.servlet.support.RequestContext(request, response, servlet, model);
        var thymeleafRequestContext = new org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext(springRequestContext, request);
        model.put(org.thymeleaf.spring6.naming.SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT, thymeleafRequestContext);
        model.put("springRequestContext", springRequestContext);
        model.put(org.thymeleaf.spring6.naming.SpringContextVariableNames.SPRING_REQUEST_CONTEXT, springRequestContext);
        model.forEach(request::setAttribute);
        for(String template:List.of("customer/payments", "customer/profile", "admin/payments", "admin/packages", "admin/package-form", "admin/discounts", "admin/discount-form", "admin/services", "admin/service-form", "admin/feedback", "admin/reports")) {
            String html=engine.process(template,new WebContext(exchange,Locale.ENGLISH,model));
            assertTrue(html.contains("parkngo-mark.svg"),template+" missing shared brand");
            Files.writeString(output.resolve(template.replace('/','-')+".html"),html);
        }
        model.put("facility",null);model.put("facilities",List.of());
        for(String template:List.of("customer/dashboard","staff/dashboard","staff/parking-spaces","admin/dashboard")) assertDoesNotThrow(()->engine.process(template,new WebContext(exchange,Locale.ENGLISH,model)),template);
    }

    @Test
    void facilityDetailsRendersPopulatedFloorSummariesAndRecentActivity() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var request = new MockHttpServletRequest(servlet);
        var response = new MockHttpServletResponse();
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, response);

        var f = new ParkingFacility();
        f.setId(1L);
        f.setName("City Centre Complex");
        f.setAddress("Colombo 03");
        f.setContactNumber("011-2345678");
        f.setCity("Colombo");
        f.setOpeningTime(java.time.LocalTime.of(6, 0));
        f.setClosingTime(java.time.LocalTime.of(23, 0));

        var model = new HashMap<String, Object>();
        model.put("facility", f);
        model.put("totalSpaces", 50);
        model.put("occupiedSpaces", 25);
        model.put("availableSpaces", 20);
        model.put("maintenanceSpaces", 5);
        model.put("evSpaces", 4);
        model.put("accessibleSpaces", 3);
        model.put("services", List.of());

        var floorSummaries = List.of(
                new com.example.parkngo.dashboard.dto.FloorSummary(1, 25, 40),
                new com.example.parkngo.dashboard.dto.FloorSummary(2, 25, 60)
        );
        model.put("floorSummaries", floorSummaries);

        var recentActivity = List.of(
                new com.example.parkngo.dashboard.dto.FacilityActivityRow(
                        LocalDateTime.now().minusMinutes(15), "WP-CAA-1234", "Check-in", "A-101", 1
                ),
                new com.example.parkngo.dashboard.dto.FacilityActivityRow(
                        LocalDateTime.now().minusMinutes(5), "WP-CAB-5678", "Check-out", "B-205", 2
                )
        );
        model.put("recentActivity", recentActivity);

        String html = engine.process("admin/facility-details", new WebContext(exchange, Locale.ENGLISH, model));
        assertNotNull(html);
        assertTrue(html.contains("Level 1"), "Template must render Level 1");
        assertTrue(html.contains("40%"), "Template must render 40% occupancy for floor 1");
        assertTrue(html.contains("Level 2"), "Template must render Level 2");
        assertTrue(html.contains("60%"), "Template must render 60% occupancy for floor 2");
        assertTrue(html.contains("WP-CAA-1234"), "Template must render vehicle plate");
        assertTrue(html.contains("WP-CAB-5678"), "Template must render second vehicle plate");
        assertTrue(html.contains("Check-in"), "Template must render activity action");
    }
}
