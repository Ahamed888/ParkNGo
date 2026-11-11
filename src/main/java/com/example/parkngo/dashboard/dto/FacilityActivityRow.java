package com.example.parkngo.dashboard.dto;

import java.time.LocalDateTime;

public record FacilityActivityRow(LocalDateTime time, String plate, String action, String space, Integer level) {}
