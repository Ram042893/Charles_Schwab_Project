package com.schwab.shortener.shortener;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, String> {
    Optional<UrlMapping> findByCode(String code);
    boolean existsByCode(String code);
    List<UrlMapping> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UrlMapping u set u.clickCount = u.clickCount + 1 where u.code = :code")
    int incrementClickCount(String code);
}
