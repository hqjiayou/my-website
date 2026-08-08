package com.mywebsite.site.service;

import com.mywebsite.site.dto.SiteAuthMeResponseDto;

public interface SiteAuthMeService {

    SiteAuthMeResponseDto loadCurrentSiteUserContext();
}
