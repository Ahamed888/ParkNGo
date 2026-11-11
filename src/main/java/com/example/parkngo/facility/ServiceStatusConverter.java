package com.example.parkngo.facility;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ServiceStatusConverter implements AttributeConverter<FacilityService.ServiceStatus, String> {

    @Override
    public String convertToDatabaseColumn(FacilityService.ServiceStatus attribute) {
        if (attribute == null) {
            return "ACTIVE";
        }
        return (attribute == FacilityService.ServiceStatus.AVAILABLE || attribute == FacilityService.ServiceStatus.ACTIVE)
                ? "ACTIVE"
                : "INACTIVE";
    }

    @Override
    public FacilityService.ServiceStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return FacilityService.ServiceStatus.AVAILABLE;
        }
        String upper = dbData.trim().toUpperCase();
        if ("ACTIVE".equals(upper) || "AVAILABLE".equals(upper)) {
            return FacilityService.ServiceStatus.AVAILABLE;
        }
        return FacilityService.ServiceStatus.UNAVAILABLE;
    }
}
