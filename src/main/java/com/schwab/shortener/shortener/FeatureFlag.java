package com.schwab.shortener.shortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @Column(length = 60)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 255)
    private String description;
}
