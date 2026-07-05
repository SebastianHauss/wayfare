package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.ClickEvent;
import com.sebastianhauss.wayfare.repository.projection.DailyCount;
import com.sebastianhauss.wayfare.repository.projection.LabelCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    // Insert keyed by short_code via a subselect so the redirect path never has
    // to load the ShortUrl entity just to learn its id (the Redis cache-hit path
    // only knows the code and the target URL).
    @Modifying
    @Query(value = """
            INSERT INTO click_events (short_url_id, referrer_domain, country, device_type, browser)
            SELECT id, :referrer, :country, :device, :browser FROM short_urls WHERE short_code = :code
            """, nativeQuery = true)
    void recordClick(@Param("code") String code,
                     @Param("referrer") String referrerDomain,
                     @Param("country") String country,
                     @Param("device") String deviceType,
                     @Param("browser") String browser);

    @Query(value = """
            SELECT to_char(date_trunc('day', clicked_at), 'YYYY-MM-DD') AS day, COUNT(*) AS count
            FROM click_events
            WHERE short_url_id = :shortUrlId AND clicked_at >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DailyCount> clicksByDay(@Param("shortUrlId") Long shortUrlId, @Param("since") Instant since);

    @Query(value = """
            SELECT COALESCE(referrer_domain, 'Direct') AS label, COUNT(*) AS count
            FROM click_events
            WHERE short_url_id = :shortUrlId
            GROUP BY 1
            ORDER BY count DESC, label
            LIMIT 6
            """, nativeQuery = true)
    List<LabelCount> topReferrers(@Param("shortUrlId") Long shortUrlId);

    @Query(value = """
            SELECT country AS label, COUNT(*) AS count
            FROM click_events
            WHERE short_url_id = :shortUrlId AND country IS NOT NULL
            GROUP BY 1
            ORDER BY count DESC, label
            LIMIT 6
            """, nativeQuery = true)
    List<LabelCount> topCountries(@Param("shortUrlId") Long shortUrlId);

    @Query(value = """
            SELECT COALESCE(device_type, 'unknown') AS label, COUNT(*) AS count
            FROM click_events
            WHERE short_url_id = :shortUrlId
            GROUP BY 1
            ORDER BY count DESC, label
            """, nativeQuery = true)
    List<LabelCount> deviceBreakdown(@Param("shortUrlId") Long shortUrlId);
}
