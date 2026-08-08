package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceConfigV1Dto;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceDetailDto;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearancePutRequest;

public interface AdminAppearanceService {

    AdminAppearanceDetailDto getAppearance(long siteId);

    AdminAppearanceDetailDto putAppearance(long siteId, AdminAppearancePutRequest request);

    /** Create 事务内插入；调用前须已通过 {@link #requireValidConfigV1}。 */
    void insertForSite(long siteId, AdminAppearanceConfigV1Dto config);

    void softDeleteBySiteId(long siteId);

    void requireValidConfigV1(AdminAppearanceConfigV1Dto config);

    void requireSchemaVersionV1(Integer schemaVersion);
}
