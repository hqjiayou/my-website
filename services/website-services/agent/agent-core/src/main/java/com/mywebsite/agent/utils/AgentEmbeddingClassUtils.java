package com.mywebsite.agent.utils;

import com.mywebsite.agent.dto.AgentEmbeddingClass;
import com.mywebsite.agent.dto.AgentModelSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

/** 嵌入类的分类键、去重上限，以及索引记录的类名集合。admin 与 agent-app 共用这一套。 */
public final class AgentEmbeddingClassUtils {

    public static final int MAX_EMBEDDING_CLASSES = 4;

    private static final String GOOGLE_GLA_PREFIX = "google-gla:";
    private static final String GOOGLE_PREFIX = "google:";

    private AgentEmbeddingClassUtils() {}

    /** 与 Python {@code embedding_class_name} 相同：{@code google-gla:} 并入 {@code google:}。 */
    public static String className(String spec) {
        if (!StringUtils.hasText(spec)) {
            return null;
        }
        String text = spec.trim();
        if (text.startsWith(GOOGLE_GLA_PREFIX)) {
            return GOOGLE_PREFIX + text.substring(GOOGLE_GLA_PREFIX.length());
        }
        return text;
    }

    /**
     * 按分类键保留先出现的入口，超过 {@link #MAX_EMBEDDING_CLASSES} 的不再送出。
     * 保存入口本身不因此失败。
     */
    public static List<AgentModelSelection> dedupe(List<AgentModelSelection> inOrder) {
        if (inOrder == null || inOrder.isEmpty()) {
            return List.of();
        }
        List<AgentModelSelection> chosen = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AgentModelSelection item : inOrder) {
            if (item == null || !StringUtils.hasText(item.embeddingSpec())) {
                continue;
            }
            String name = className(item.embeddingSpec());
            if (name == null || !seen.add(name)) {
                continue;
            }
            chosen.add(item);
            if (chosen.size() == MAX_EMBEDDING_CLASSES) {
                break;
            }
        }
        return List.copyOf(chosen);
    }

    /** 空列表写成 null，报文里就不出现 {@code embeddingClasses}，执行层继续走单套或进程嵌入。 */
    public static List<AgentEmbeddingClass> wire(List<AgentModelSelection> classes) {
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        List<AgentEmbeddingClass> wired = new ArrayList<>();
        for (AgentModelSelection item : classes) {
            if (item == null || !StringUtils.hasText(item.embeddingSpec())) {
                continue;
            }
            wired.add(new AgentEmbeddingClass(item.embeddingSpec().trim(), item.apiKey(), item.baseUrl()));
        }
        return wired.isEmpty() ? null : List.copyOf(wired);
    }

    /** 成功写入的类名，排序后用换行拼接。一类时没有换行，和旧的单 spec 记录同一形状。 */
    public static String canonical(List<AgentModelSelection> classes) {
        return join(namesOf(classes));
    }

    public static boolean matchesStored(String stored, List<AgentModelSelection> classes) {
        return Objects.equals(join(storedNames(stored)), canonical(classes));
    }

    /** 已记下的类名非空，而且当前登记的类名真包含它。 */
    public static boolean isProperSubset(String stored, List<AgentModelSelection> classes) {
        Set<String> previous = storedNames(stored);
        Set<String> current = namesOf(classes);
        return !previous.isEmpty() && current.containsAll(previous) && current.size() > previous.size();
    }

    public static List<AgentModelSelection> missing(String stored, List<AgentModelSelection> classes) {
        if (classes == null || classes.isEmpty()) {
            return List.of();
        }
        Set<String> previous = storedNames(stored);
        List<AgentModelSelection> missing = new ArrayList<>();
        for (AgentModelSelection item : classes) {
            String name = item == null ? null : className(item.embeddingSpec());
            if (name != null && !previous.contains(name)) {
                missing.add(item);
            }
        }
        return List.copyOf(missing);
    }

    private static Set<String> namesOf(List<AgentModelSelection> classes) {
        Set<String> names = new HashSet<>();
        if (classes == null) {
            return names;
        }
        for (AgentModelSelection item : classes) {
            String name = item == null ? null : className(item.embeddingSpec());
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static Set<String> storedNames(String stored) {
        Set<String> names = new HashSet<>();
        if (!StringUtils.hasText(stored)) {
            return names;
        }
        for (String part : stored.split("\n")) {
            String name = className(part);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static String join(Set<String> names) {
        if (names.isEmpty()) {
            return null;
        }
        return String.join("\n", names.stream().sorted().toList());
    }
}
