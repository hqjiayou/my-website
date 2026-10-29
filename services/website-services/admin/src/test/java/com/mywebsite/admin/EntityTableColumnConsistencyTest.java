package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 同表双实体列一致性守卫：admin（{@code com.mywebsite.admin.entity}）与 agent-core
 * （{@code com.mywebsite.agent.entity}）两边都对部分平台表各自维护一份 {@code @TableName}
 * 实体（见各实体类上的"双映射"说明），本测试反射枚举两边实体并按表名配对，防止其中一边改列
 * 而另一边悄悄漏改。
 *
 * <p>比对范围：两边实体显式声明的列（{@code @TableId}/{@code @TableField} 注解的
 * {@code value()}，非空才算数），加上公共父类 {@code AbstractAuditableEntity} 的审计列
 * （该父类对每个字段都显式注解了列名，两边继承自同一父类，口径天然一致）。未加注解、走
 * MyBatis-Plus 默认驼峰转下划线约定的字段不计入——同名字段的默认约定两边必然一致，真正会
 * 悄悄分叉的是显式声明（自定义列名、typeHandler 等）。
 *
 * <p>已知列集合本就不等、不纳入下方断言（留给审查方裁决是否需要补齐，而非测试失败）：
 *
 * <ul>
 *   <li><b>agent_ai_message</b>：agent-core {@code AgentAiMessageEntity} 显式声明
 *       {@code attachments}（V25，USER 消息图片附件，{@code typeHandler = JsonbStringTypeHandler}），
 *       admin {@code AdminAiMessageEntity} 未声明该列（后台管理侧不读写附件）。
 * </ul>
 */
class EntityTableColumnConsistencyTest {

    private static final String ADMIN_ENTITY_PACKAGE = "com.mywebsite.admin.entity";
    private static final String AGENT_CORE_ENTITY_PACKAGE = "com.mywebsite.agent.entity";

    /** 见类注释：列集合本就不等的已知对子，不纳入一致性断言。 */
    private static final Set<String> KNOWN_MISMATCHED_TABLES = Set.of("agent_ai_message");

    @Test
    void adminAndAgentCoreEntitiesOnTheSameTableDeclareTheSameColumns() {
        Map<String, Class<?>> adminEntitiesByTable = entitiesByTableName(ADMIN_ENTITY_PACKAGE);
        Map<String, Class<?>> agentCoreEntitiesByTable = entitiesByTableName(AGENT_CORE_ENTITY_PACKAGE);

        Set<String> sharedTables = new TreeSet<>(adminEntitiesByTable.keySet());
        sharedTables.retainAll(agentCoreEntitiesByTable.keySet());

        // agent_model_entry 必须在覆盖范围内：平台模型入口是 admin / agent-core 双映射的典型代表。
        assertThat(sharedTables).contains("agent_model_entry");

        sharedTables.removeAll(KNOWN_MISMATCHED_TABLES);

        List<String> failures = new ArrayList<>();
        for (String table : sharedTables) {
            Class<?> adminEntity = adminEntitiesByTable.get(table);
            Class<?> agentEntity = agentCoreEntitiesByTable.get(table);
            Set<String> adminColumns = declaredColumns(adminEntity);
            Set<String> agentColumns = declaredColumns(agentEntity);
            if (!adminColumns.equals(agentColumns)) {
                Set<String> onlyInAdmin = new TreeSet<>(adminColumns);
                onlyInAdmin.removeAll(agentColumns);
                Set<String> onlyInAgentCore = new TreeSet<>(agentColumns);
                onlyInAgentCore.removeAll(adminColumns);
                failures.add(String.format(
                        "表 %s：%s 独有列 %s；%s 独有列 %s",
                        table,
                        adminEntity.getSimpleName(),
                        onlyInAdmin,
                        agentEntity.getSimpleName(),
                        onlyInAgentCore));
            }
        }
        assertThat(failures).isEmpty();
    }

    /** 枚举 {@code basePackage} 下带 {@code @TableName} 的实体类，按表名建立索引。 */
    private static Map<String, Class<?>> entitiesByTableName(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

        Map<String, Class<?>> entitiesByTable = new HashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            String className = candidate.getBeanClassName();
            Class<?> entityType;
            try {
                entityType = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("实体类加载失败：" + className, e);
            }
            TableName tableName = entityType.getAnnotation(TableName.class);
            entitiesByTable.put(tableName.value(), entityType);
        }
        return entitiesByTable;
    }

    /**
     * 提取实体显式声明的列集合：{@code @TableId}/{@code @TableField} 注解且 {@code value()} 非空
     * 才算一列，沿类层级（含父类，不含 {@code Object}）收集。
     */
    private static Set<String> declaredColumns(Class<?> entityType) {
        Set<String> columns = new HashSet<>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                TableId tableId = field.getAnnotation(TableId.class);
                if (tableId != null && !tableId.value().isBlank()) {
                    columns.add(tableId.value());
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && tableField.exist() && !tableField.value().isBlank()) {
                    columns.add(tableField.value());
                }
            }
        }
        return columns;
    }
}
