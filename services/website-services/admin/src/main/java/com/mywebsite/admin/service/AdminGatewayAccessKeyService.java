package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyDto;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueRequest;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueResponse;
import java.util.List;

public interface AdminGatewayAccessKeyService {

    /** 生成明文 key，只存其哈希；明文只在本次响应中返回一次。 */
    AdminGatewayAccessKeyIssueResponse issue(AdminGatewayAccessKeyIssueRequest request);

    List<AdminGatewayAccessKeyDto> list();

    /** 置 revokedAt；已吊销的重复调用保持原样幂等返回。 */
    AdminGatewayAccessKeyDto revoke(long id);
}
