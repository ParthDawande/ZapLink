package com.zaplink.event;

import java.time.LocalDateTime;

public record ClickRecordedEvent(Long linkId, LocalDateTime clickedAt) {}
