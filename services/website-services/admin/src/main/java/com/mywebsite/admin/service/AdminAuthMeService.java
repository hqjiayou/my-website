package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAuthMeDto;

public interface AdminAuthMeService {

    AdminAuthMeDto loadCurrentAdminContext();
}
