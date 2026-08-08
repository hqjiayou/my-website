package com.mywebsite.admin.service.impl;

import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.entity.AdminPermissionEntity;
import com.mywebsite.admin.mapper.AdminMenuMapper;
import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import com.mywebsite.admin.service.AdminMenuService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    private final AdminMenuMapper adminMenuMapper;

    public AdminMenuServiceImpl(AdminMenuMapper adminMenuMapper) {
        this.adminMenuMapper = adminMenuMapper;
    }

    @Override
    public List<AdminMenuNodeDto> buildMenuTree(AdminCurrentAuthorizationSnapshot authorization) {
        List<AdminPermissionEntity> rows = adminMenuMapper.selectAllActiveMenus();
        if (authorization.superAdmin()) {
            return buildTree(rows);
        }
        return buildTree(selectVisibleWithAncestors(rows, new HashSet<>(authorization.permissions())));
    }

    @Override
    public List<AdminMenuNodeDto> listActiveMenuTree() {
        return buildTree(adminMenuMapper.selectAllActiveMenus());
    }

    /**
     * A-4：非超管可见菜单 = 已授权的 MENU 节点 + 其沿 {@code parent_id} 向上的全部祖先容器。
     *
     * <p>祖先仅作为「结构容器」补入以保持层级完整（前端据此分组），并不隐含授予任何权限——
     * 权限判定始终以快照 {@code permissions} 为准，本方法不修改快照。
     */
    private static List<AdminPermissionEntity> selectVisibleWithAncestors(
            List<AdminPermissionEntity> rows, Set<String> allowedCodes) {
        Map<Long, AdminPermissionEntity> byId = new HashMap<>();
        for (AdminPermissionEntity row : rows) {
            byId.put(row.getId(), row);
        }

        Set<Long> visibleIds = new HashSet<>();
        for (AdminPermissionEntity row : rows) {
            if (!allowedCodes.contains(row.getPermissionCode())) {
                continue;
            }
            // 从已授权节点沿父链向上补齐；add 返回 false 表示已访问，兼作脏数据环保护。
            AdminPermissionEntity current = row;
            while (current != null && visibleIds.add(current.getId())) {
                Long parentId = current.getParentId();
                current = parentId == null ? null : byId.get(parentId);
            }
        }

        return rows.stream().filter(row -> visibleIds.contains(row.getId())).toList();
    }

    private static List<AdminMenuNodeDto> buildTree(List<AdminPermissionEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, MutableMenuNode> byId = new HashMap<>();
        for (AdminPermissionEntity row : rows) {
            byId.put(row.getId(), new MutableMenuNode(row));
        }

        List<MutableMenuNode> roots = new ArrayList<>();
        for (AdminPermissionEntity row : rows) {
            MutableMenuNode node = byId.get(row.getId());
            Long parentId = row.getParentId();
            if (parentId == null || !byId.containsKey(parentId)) {
                roots.add(node);
            } else {
                byId.get(parentId).children.add(node);
            }
        }

        return roots.stream().sorted(MutableMenuNode.NODE_COMPARATOR).map(MutableMenuNode::toDto).toList();
    }

    private static final class MutableMenuNode {

        private static final Comparator<MutableMenuNode> NODE_COMPARATOR = Comparator.comparingInt(
                        (MutableMenuNode node) -> node.row.getSortOrder() != null ? node.row.getSortOrder() : 0)
                .thenComparing(node -> node.row.getPermissionCode());

        private final AdminPermissionEntity row;

        private final List<MutableMenuNode> children = new ArrayList<>();

        private MutableMenuNode(AdminPermissionEntity row) {
            this.row = row;
        }

        private AdminMenuNodeDto toDto() {
            List<AdminMenuNodeDto> childDtos =
                    children.stream().sorted(NODE_COMPARATOR).map(MutableMenuNode::toDto).toList();
            int sort = row.getSortOrder() != null ? row.getSortOrder() : 0;
            return new AdminMenuNodeDto(
                    row.getPermissionCode(),
                    row.getPermissionName(),
                    row.getRoutePath(),
                    row.getIcon(),
                    sort,
                    childDtos);
        }
    }
}
