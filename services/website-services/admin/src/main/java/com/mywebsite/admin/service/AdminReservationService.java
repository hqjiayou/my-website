package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationCreateRequest;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationDetailDto;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationListItemDto;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationListQuery;
import com.mywebsite.admin.dto.AdminReservationDtos.AdminReservationStatusUpdateRequest;
import com.mywebsite.common.page.PageResult;

public interface AdminReservationService {

    PageResult<AdminReservationListItemDto> listReservations(long siteId, AdminReservationListQuery query);

    AdminReservationDetailDto getReservation(long siteId, long reservationId);

    AdminReservationDetailDto createReservation(long siteId, AdminReservationCreateRequest request);

    AdminReservationDetailDto updateStatus(
            long siteId, long reservationId, AdminReservationStatusUpdateRequest request);

    void deleteReservation(long siteId, long reservationId);

    /** 站点软删联动：软删该站点全部预约。 */
    void softDeleteBySiteId(long siteId);
}
