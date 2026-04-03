package com.kursach.inventory.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "action_logs")
public class ActionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts = Instant.now();

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 2000)
    private String message;

    public ActionLog() {}
    public ActionLog(String actor, String message) {
        this.actor = actor;
        this.message = message;
        this.ts = Instant.now();
    }

    public Long getId() { return id; }
    public Instant getTs() { return ts; }
    public String getActor() { return actor; }
    public String getMessage() { return message; }

    public void setId(Long id) { this.id = id; }
    public void setTs(Instant ts) { this.ts = ts; }
    public void setActor(String actor) { this.actor = actor; }
    public void setMessage(String message) { this.message = message; }
}
