package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.constant.AdminRbacMessageKeys;
import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuCreateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuUpdateRequest;
import com.mywebsite.admin.entity.AdminPermissionEntity;
import com.mywebsite.admin.mapper.AdminMenuMapper;
import com.mywebsite.admin.mapper.AdminPermissionMapper;
import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import com.mywebsite.admin.service.AdminMenuService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    private static final Pattern MENU_CODE = Pattern.compile("^admin:menu:[a-z][a-z0-9]*([.:][a-z0-9]+)*$");

    private static final String STATUS_DISABLED = "DISABLED";

    private final AdminMenuMapper adminMenuMapper;

    private final AdminPermissionMapper adminPermissionMapper;

    public AdminMenuServiceImpl(AdminMenuMapper adminMenuMapper, AdminPermissionMapper adminPermissionMapper) {
        this.adminMenuMapper = adminMenuMapper;
        this.adminPermissionMapper = adminPermissionMapper;
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
    public List<AdminMenuNodeDto> listManageTree() {
        return buildTree(adminMenuMapper.selectAllMenus());
    }

    @Override
    public AdminMenuNodeDto createMenu(AdminMenuCreateRequest request) {
        String code = request.menuCode().trim();
        requireMenuCode(code);
        requireStatus(request.status());
        requireParent(request.parentId(), null);
        if (findByCode(code) != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminRbacMessageKeys.MENU_CODE_DUPLICATE);
        }
        AdminPermissionEntity entity = new AdminPermissionEntity();
        entity.setPermissionCode(code);
        entity.setPermissionName(request.menuName().trim());
        entity.setResourceType(AdminRbacConstants.RESOURCE_TYPE_MENU);
        entity.setResourceKey(code.substring("admin:menu:".length()));
        entity.setStatus(request.status());
        entity.setParentId(request.parentId());
        entity.setSortOrder(request.sort());
        entity.setRoutePath(blankToNull(request.path()));
        entity.setIcon(blankToNull(request.icon()));
        try {
            adminPermissionMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminRbacMessageKeys.MENU_CODE_DUPLICATE);
        }
        return toNode(entity, List.of());
    }

    @Override
    public AdminMenuNodeDto updateMenu(long id, AdminMenuUpdateRequest request) {
        AdminPermissionEntity menu = requireMenu(id);
        requireStatus(request.status());
        requireParent(request.parentId(), id);
        menu.setPermissionName(request.menuName().trim());
        menu.setParentId(request.parentId());
        menu.setSortOrder(request.sort());
        menu.setRoutePath(blankToNull(request.path()));
        menu.setIcon(blankToNull(request.icon()));
        menu.setStatus(request.status());
        adminPermissionMapper.updateById(menu);
        return toNode(menu, List.of());
    }

    @Override
    public void deleteMenu(long id) {
        requireMenu(id);
        Long childCount = adminPermissionMapper.selectCount(
                new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.MENU_HAS_CHILDREN);
        }
        adminPermissionMapper.deleteById(id);
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
                // 父节点不在本批结果里（停用、未授权且无法补祖先）时不升成根，避免侧栏把子菜单顶到一级。
                if (parentId == null) {
                    roots.add(node);
                }
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
            return toNode(row, childDtos);
        }
    }

    private AdminPermissionEntity requireMenu(long id) {
        AdminPermissionEntity row = adminPermissionMapper.selectById(id);
        if (row == null || !AdminRbacConstants.RESOURCE_TYPE_MENU.equals(row.getResourceType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminRbacMessageKeys.MENU_NOT_FOUND);
        }
        return row;
    }

    private AdminPermissionEntity findByCode(String code) {
        return adminPermissionMapper.selectOne(
                new LambdaQueryWrapper<AdminPermissionEntity>().eq(AdminPermissionEntity::getPermissionCode, code));
    }

    private void requireMenuCode(String code) {
        if (!MENU_CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.MENU_CODE_INVALID);
        }
    }

    private static void requireStatus(String status) {
        if (!AdminRbacConstants.STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.MENU_STATUS_INVALID);
        }
    }

    /** {@code selfId} 非空时，新父节点不能是自己或自己的子孙。 */
    private void requireParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.MENU_PARENT_INVALID);
        }
        AdminPermissionEntity parent = adminPermissionMapper.selectById(parentId);
        if (parent == null || !AdminRbacConstants.RESOURCE_TYPE_MENU.equals(parent.getResourceType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.MENU_PARENT_INVALID);
        }
        if (selfId != null && reaches(parentId, selfId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.MENU_PARENT_INVALID);
        }
    }

    /** 从 {@code startId} 沿 parent_id 向上，是否会走到 {@code targetId}。 */
    private boolean reaches(Long startId, Long targetId) {
        Map<Long, Long> parentById = new HashMap<>();
        for (AdminPermissionEntity row : adminMenuMapper.selectAllMenus()) {
            parentById.put(row.getId(), row.getParentId());
        }
        Long current = startId;
        Set<Long> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current.equals(targetId)) {
                return true;
            }
            current = parentById.get(current);
        }
        return false;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static AdminMenuNodeDto toNode(AdminPermissionEntity row, List<AdminMenuNodeDto> children) {
        int sort = row.getSortOrder() != null ? row.getSortOrder() : 0;
        return new AdminMenuNodeDto(
                row.getPermissionCode(),
                row.getPermissionName(),
                row.getRoutePath(),
                row.getIcon(),
                sort,
                children,
                row.getId(),
                row.getStatus());
    }
}
