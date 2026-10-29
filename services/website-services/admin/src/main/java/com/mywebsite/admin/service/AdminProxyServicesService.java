package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesApiKeysDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesLoginDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesLoginStatusDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesStatusDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesUsageDto;

public interface AdminProxyServicesService {

    AdminProxyServicesStatusDto status();

    AdminProxyServicesApiKeysDto listApiKeys();

    String createApiKey(String apiKey);

    void deleteApiKey(String apiKey);

    AdminProxyServicesLoginDto startLogin(String provider);

    AdminProxyServicesLoginStatusDto loginStatus(String state);

    AdminProxyServicesUsageDto usage();
}
