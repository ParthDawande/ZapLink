package com.zaplink.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "clicks")
@Getter
@Setter
@NoArgsConstructor
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    // Set explicitly by the async listener at event creation time (not listener-fire time).
    // DB DEFAULT CURRENT_TIMESTAMP remains as a fallback but is not used in normal operation.
    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;
}
