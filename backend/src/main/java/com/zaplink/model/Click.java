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

    // DB sets this via DEFAULT CURRENT_TIMESTAMP; we never write it from Java.
    @Column(name = "clicked_at", insertable = false, updatable = false)
    private LocalDateTime clickedAt;
}
