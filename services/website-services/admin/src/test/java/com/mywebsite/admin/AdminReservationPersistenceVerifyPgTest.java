package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.admin.entity.AdminReservationEntity;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.mapper.AdminReservationMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Reservation 持久层真实 PostgreSQL：site_reservation 表、FK、软删。
 *
 * <p>{@code mvn test -Ppersistence-verify-pg -pl admin -am -Dtest=AdminReservationPersistenceVerifyPgTest}
 */
@Tag("verifyPg")
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("verify-pg")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminReservationPersistenceVerifyPgTest {

    @Autowired
    private AdminSiteMapper adminSiteMapper;

    @Autowired
    private AdminReservationMapper adminReservationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void siteReservationRoundTripSoftDeleteAndStatusCheck() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode("pg_rs_" + suffix);
        site.setSiteName("PG Reservation");
        site.setSiteType("SALON");
        site.setStatus("ACTIVE");
        adminSiteMapper.insert(site);
        assertThat(site.getId()).isNotNull();

        AdminReservationEntity reservation = new AdminReservationEntity();
        reservation.setSiteId(site.getId());
        reservation.setCustomerName("Verify User");
        reservation.setPhone("09011112222");
        reservation.setServiceType("cut");
        reservation.setReservedAt(OffsetDateTime.of(2026, 8, 12, 9, 30, 0, 0, ZoneOffset.UTC));
        reservation.setStatus("NEW");
        reservation.setNote("pg note");
        adminReservationMapper.insert(reservation);
        assertThat(reservation.getId()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT site_id, customer_name, phone, service_type, status, deleted
                FROM site_reservation WHERE id = ?
                """,
                reservation.getId());
        assertThat(row.get("site_id")).isEqualTo(site.getId());
        assertThat(row.get("customer_name")).isEqualTo("Verify User");
        assertThat(row.get("phone")).isEqualTo("09011112222");
        assertThat(row.get("service_type")).isEqualTo("cut");
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("deleted")).isEqualTo(false);

        adminReservationMapper.deleteById(reservation.getId());
        Integer deletedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM site_reservation WHERE id = ? AND deleted = TRUE
                """,
                Integer.class,
                reservation.getId());
        assertThat(deletedCount).isEqualTo(1);
        assertThat(adminReservationMapper.selectById(reservation.getId())).isNull();
    }
}
