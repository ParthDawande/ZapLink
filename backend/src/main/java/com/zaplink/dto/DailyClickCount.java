package com.zaplink.dto;

import java.time.LocalDate;

public record DailyClickCount(LocalDate date, Long count) {}
