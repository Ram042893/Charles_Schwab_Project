package com.schwab.shortener.shortener;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, String> {
    List<ClickEvent> findByCodeOrderByClickedAtDesc(String code);
    long countByCode(String code);
}
