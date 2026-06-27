package com.bravoscribe.journalservice.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tags", schema = "journal",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String name;

    public Tag() {}

    public Tag(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
