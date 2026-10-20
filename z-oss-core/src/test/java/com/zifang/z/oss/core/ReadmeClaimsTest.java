package com.zifang.z.oss.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * README 里每一条定量主张都必须能从源码重算出来 —— 广告出去的话就是承诺。
 *
 * <p>为什么要有这一层：本仓 README 把「3 个 module」「3 个 Controller」「3 个 Mapper、
 * 3 组 Service 接口与实现」「建 5 张表」「当前版本 1.0.4」写成了硬数字，而每加一张表、
 * 每加一个模块、每加一个 Controller 都会漂一次。没有任何尺子去问代码「现在还是吗」，
 * 那这些数字的保质期就是上一次编辑的时刻。</p>
 *
 * <p>口径：缺一条主张 = 红（不许「正则没匹配上就跳过」，那正是空跑）；
 * 数值不等 = 红且把<b>重算值</b>打进消息（红了要知道真数是多少，否则只是又一处猜）。
 * 尺自己的牙口由 {@link #rulerActuallyCondemnsWrongNumbers()} 用合成 README 证明，
 * 而「不许冤枉健康页」由 {@link #readmeClaimsMatchTheCode()} 本身证明。</p>
 *
 * <p>与骨架的一处有意差别：核对结果<b>一次全报</b>（收集齐所有不一致再抛 AssertionError），
 * 而不是撞上第一条就停。理由是本仓 README 已经有存量漂移，撞停就只会报出第一条，
 * 后面的漂移要改一次 README 才能看见下一次 —— 那等于把尺子退化成「一次只说一句」。
 * 牙口测试也因此能证明「改之前这条主张是对得上的」：改之前的消息里不应出现该主张，
 * 改之后才出现 —— 两者一夹，变异到底有没有被尺子抓住就没有解释空间了。</p>
 */
class ReadmeClaimsTest {

    /** 仓根：向上找同时有 README.md 与 pom.xml 的目录（测试跑在模块目录，user.dir 不是仓根）。 */
    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 4 && p != null; i++, p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("README.md")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("定位仓根失败：user.dir=" + System.getProperty("user.dir"));
    }

    private static String read(Path f) {
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String norm(Path p) {
        return p.toString().replace('\\', '/');
    }

    /** 仓内全部 java 源文件（排除构建产物与参考树）。 */
    private static List<Path> javaSources(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !norm(p).contains("/target/"))
                    .filter(p -> !norm(p).contains("/node_modules/"))
                    .filter(p -> !norm(p).contains("/.git/"))
                    .filter(p -> !norm(p).contains("/_code_temp/"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 名为 dirName 的目录里<b>直属</b>的 .java 个数（不含子目录，如 service/impl）。
     *
     * <p>只数 {@code src/main} 下的目录 —— README 说的「N 个 Mapper / N 组 Service」指的是生产代码，
     * {@code src/test} 下同名目录里躺的是测试类（守卫自己就躺在 src/test 里）。
     * 第一版尺没加这个过滤，Mapper 数会多算测试类，直接判红。</p>
     */
    private static long countInDir(Path root, String dirName) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals(dirName))
                    .filter(p -> norm(p).contains("/src/main/"))
                    .flatMap(d -> {
                        try (Stream<Path> c = Files.list(d)) {
                            return c.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())
                                    .stream();
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@code service/impl} 目录里的 .java 个数（Service 实现类）。
     *
     * <p>不能直接用 {@code countInDir(root, "impl")} —— 本仓不止一处叫 {@code impl} 的目录
     * （provider/impl、file/impl 也在），那样会把 Provider 实现与文件门面实现一起数进来，
     * 数成 8 而不是 3。这里只认「父目录名叫 service」的那一处。</p>
     */
    private static long countServiceImpls(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("impl"))
                    .filter(p -> p.getParent() != null && p.getParent().getFileName().toString().equals("service"))
                    .filter(p -> norm(p).contains("/src/main/"))
                    .flatMap(d -> {
                        try (Stream<Path> c = Files.list(d)) {
                            return c.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())
                                    .stream();
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 某个目录名下的 .java 文件名（去扩展名），如 entity 目录下的 5 个实体类名。 */
    private static Set<String> classNamesInDir(Path root, String dirName) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals(dirName))
                    .filter(p -> norm(p).contains("/src/main/"))
                    .flatMap(d -> {
                        try (Stream<Path> c = Files.list(d)) {
                            return c.filter(f -> f.toString().endsWith(".java"))
                                    .map(f -> f.getFileName().toString().replaceAll("\\.java$", ""))
                                    .collect(Collectors.toList())
                                    .stream();
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 某个模块下 src/main 里的 *Controller.java 个数（moduleRelPath 相对仓根，如 {@code z-oss-api}）。 */
    private static long countControllers(Path root, String moduleRelPath) {
        Path base = root.resolve(moduleRelPath);
        if (!Files.isDirectory(base)) {
            throw new IllegalStateException("仓里没有模块目录：" + moduleRelPath);
        }
        try (Stream<Path> s = Files.walk(base)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .filter(p -> norm(p).contains("/src/main/"))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 全部 @TableName 锚定的表名（去重）。
     *
     * <p>只认 {@code src/main} 下的源文件 —— README 说的「N 张表」指的是生产代码的表。
     * 守卫自己就躺在 {@code src/test} 里，源文件里还留着 {@code @TableName} 这个字面量，
     * 不加这道过滤，尺子就会把自己的源码算成一张表。</p>
     */
    private static Set<String> tableNames(List<Path> sources) {
        Pattern p = Pattern.compile("@TableName\\(\"([^\"]+)\"\\)");
        Set<String> names = new TreeSet<>();
        for (Path f : sources) {
            if (!norm(f).contains("/src/main/")) {
                continue;
            }
            Matcher m = p.matcher(read(f));
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    /**
     * 根 pom 的正文（先去掉 XML 注释）。
     *
     * <p>去注释是必须的：本仓根 pom 里留着一条被注释掉的 {@code <!-- <module>_frontend</module> -->}
     * （README 明说 _frontend 不进 Maven reactor），不剔掉就会把模块数多算 1。</p>
     */
    private static String rootPomText(Path root) {
        return read(root.resolve("pom.xml")).replaceAll("(?s)<!--.*?-->", "");
    }

    /** 从根 pom 里取 <module> 条数与模块名清单。 */
    private static Set<String> moduleNames(Path root) {
        Matcher m = Pattern.compile("<module>([^<]+)</module>").matcher(rootPomText(root));
        Set<String> names = new TreeSet<>();
        while (m.find()) {
            names.add(m.group(1).trim());
        }
        return names;
    }

    /**
     * 从根 pom 里取本项目自己的 {@code <version>} 面值。
     *
     * <p>本仓<b>不是</b> CI-friendly versions（README 明确写了这一点）：根 pom 用的是普通
     * {@code <version>1.0.4</version>}，子模块 {@code <parent>} 里也写死同一个号。
     * 所以这里不能照骨架去抠 {@code <revision>} —— 本仓根本没有那个元素；
     * 得先把 {@code <parent>} 整块剔掉，否则会抠到父链的 1.1.0。</p>
     */
    private static String projectVersion(Path root) {
        String withoutParent = rootPomText(root).replaceAll("(?s)<parent>.*?</parent>", "");
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(withoutParent);
        if (!m.find()) {
            throw new IllegalStateException("根 pom 里没有 <version>");
        }
        return m.group(1).trim();
    }

    /** 根 pom 里有没有 {@code <revision>}（本仓 README 明确说没有）。 */
    private static boolean hasRevision(Path root) {
        return Pattern.compile("<revision>([^<]+)</revision>").matcher(rootPomText(root)).find();
    }

    /** 中文数字（一 ~ 十）→ 阿拉伯数字；认不出就返回 null。 */
    private static Integer chineseNumeral(String s) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        for (int i = 1; i < digits.length; i++) {
            if (digits[i].equals(s)) {
                return i;
            }
        }
        if ("十".equals(s)) {
            return 10;
        }
        return null;
    }

    /** 一次重算出的全部数字。 */
    private static final class Counts {
        final int tables;
        final long controllers;
        final long mappers;
        final long serviceIfaces;
        final long serviceImpls;
        final long entities;
        final Set<String> entityNames;
        final Set<String> tableNameSet;
        final Set<String> moduleNameSet;
        final String version;
        final boolean revisionPresent;

        Counts(int tables, long controllers, long mappers, long serviceIfaces, long serviceImpls,
               long entities, Set<String> entityNames, Set<String> tableNameSet,
               Set<String> moduleNameSet, String version, boolean revisionPresent) {
            this.tables = tables;
            this.controllers = controllers;
            this.mappers = mappers;
            this.serviceIfaces = serviceIfaces;
            this.serviceImpls = serviceImpls;
            this.entities = entities;
            this.entityNames = entityNames;
            this.tableNameSet = tableNameSet;
            this.moduleNameSet = moduleNameSet;
            this.version = version;
            this.revisionPresent = revisionPresent;
        }
    }

    private static Counts recomputed() {
        Path root = repoRoot();
        List<Path> src = javaSources(root);
        return new Counts(
                tableNames(src).size(),
                countControllers(root, "z-oss-api"),
                countInDir(root, "mapper"),
                countInDir(root, "service"),
                countServiceImpls(root),
                countInDir(root, "entity"),
                classNamesInDir(root, "entity"),
                tableNames(src),
                moduleNames(root),
                projectVersion(root),
                hasRevision(root));
    }

    /** 从一段文本里抠出所有 `反引号包住的名字`（README 用它列模块名 / 表名 / 实体名）。 */
    private static Set<String> backticked(String text) {
        Matcher m = Pattern.compile("`([^`]+)`").matcher(text);
        Set<String> names = new LinkedHashSet<>();
        while (m.find()) {
            names.add(m.group(1).trim());
        }
        return names;
    }

    /**
     * README 里逐条抠出数字并与重算值对账；任何一条对不上就抛 AssertionError，
     * 并把<b>所有</b>不一致连同真数一起打进去（撞停只会报出第一条，后面的漂移看不见）。
     */
    private static void verifyClaims(String readme, Counts c) {
        List<String> bad = new ArrayList<>();
        Matcher m;

        // 主张 1：基本信息表「当前版本 | `1.0.4`（根 POM 用的是普通 `<version>`…）」
        m = Pattern.compile("\\*\\*当前版本\\*\\*\\s*\\|\\s*`([^`]+)`").matcher(readme);
        if (!m.find()) {
            bad.add("README 里找不到「**当前版本** | `X.Y.Z`」这条版本主张");
        } else if (!c.version.equals(m.group(1))) {
            bad.add("版本号对不上：README 写 " + m.group(1) + "，根 pom <version> 是 " + c.version);
        }

        // 主张 2：「根 POM 用的是普通 `<version>`，**不是** `<revision>` + CI-friendly」
        if (!readme.contains("根 POM 用的是普通 `<version>`")) {
            bad.add("README 里找不到「根 POM 用的是普通 `<version>`」这条版本形态主张");
        } else if (c.revisionPresent) {
            bad.add("版本形态对不上：README 说根 POM 用普通 <version> 不是 <revision>，"
                    + "但根 pom 里确实有 <revision> 元素");
        }

        // 主张 3：项目结构块「…，3 个 module」
        m = Pattern.compile("，(\\d+) 个 module").matcher(readme);
        if (!m.find()) {
            bad.add("README 里找不到「N 个 module」这条主张");
        } else if (c.moduleNameSet.size() != Integer.parseInt(m.group(1))) {
            bad.add("Maven 模块数对不上：README 写 " + m.group(1)
                    + " 个，根 pom <module> 条数实数是 " + c.moduleNameSet.size());
        }

        // 主张 4：纠偏段「根 POM 的 `<modules>` 今天实实在在列了 `z-oss-common`、`z-oss-core`、
        // `z-oss-api` 三个模块」—— 模块名清单与中文数字都要对得上
        m = Pattern.compile("根 POM 的 `<modules>` 今天实实在在列了([^。]*?)([一二三四五六七八九十])\\s*个模块",
                Pattern.DOTALL).matcher(readme);
        if (!m.find()) {
            bad.add("README 里找不到「根 POM 的 `<modules>` … 列了 N 个模块」这条主张");
        } else {
            Set<String> listed = backticked(m.group(1));
            Integer said = chineseNumeral(m.group(2));
            if (said == null) {
                bad.add("README 里「列了 … 个模块」的中文数字认不出：" + m.group(2));
            } else if (said != c.moduleNameSet.size()) {
                bad.add("Maven 模块数对不上：README 说 " + said
                        + " 个模块，根 pom <module> 条数实数是 " + c.moduleNameSet.size());
            }
            if (listed.size() != c.moduleNameSet.size()) {
                bad.add("Maven 模块数对不上：README 列出 " + listed.size() + " 个模块（" + String.join("、", listed)
                        + "），根 pom <module> 条数实数是 " + c.moduleNameSet.size());
            } else if (!c.moduleNameSet.containsAll(listed)) {
                bad.add("Maven 模块名对不上：README 列了 " + String.join("、", listed)
                        + "，根 pom <modules> 实数是 " + String.join("、", c.moduleNameSet));
            }
        }

        // 主张 5：「`domain`（`OssUser`/`…` 实体、3 个 Mapper、3 组 Service 接口与实现）」
        m = Pattern.compile("`domain`（([^）]*)实体、(\\d+) 个 Mapper、(\\d+) 组 Service 接口与实现）")
                .matcher(readme);
        if (!m.find()) {
            bad.add("README 里找不到「`domain`（… 实体、N 个 Mapper、N 组 Service 接口与实现）」这条主张");
        } else {
            Set<String> listedEntities = backticked(m.group(1));
            if (listedEntities.size() != c.entities) {
                bad.add("实体数对不上：README 列出 " + listedEntities.size() + " 个实体（"
                        + String.join("、", listedEntities) + "），domain/entity 下实数是 " + c.entities);
            } else if (!c.entityNames.containsAll(listedEntities)) {
                bad.add("实体名对不上：README 列了 " + String.join("、", listedEntities)
                        + "，domain/entity 下实数是 " + String.join("、", c.entityNames));
            }
            if (c.mappers != Long.parseLong(m.group(2))) {
                bad.add("Mapper 数对不上：README 写 " + m.group(2)
                        + " 个，src/main 下 mapper 目录直属 .java 实数是 " + c.mappers);
            }
            if (c.serviceIfaces != Long.parseLong(m.group(3))) {
                bad.add("Service 接口数对不上：README 写 " + m.group(3)
                        + " 组，src/main 下 service 目录直属 .java 实数是 " + c.serviceIfaces);
            }
            if (c.serviceImpls != c.serviceIfaces) {
                bad.add("Service 实现数对不上：README 写 " + m.group(3)
                        + " 组接口与实现，实数是接口 " + c.serviceIfaces + " 个 / 实现 " + c.serviceImpls + " 个");
            }
        }

        // 主张 6：「**`z-oss-api`** — 3 个 Controller」
        m = Pattern.compile("— (\\d+) 个 Controller").matcher(readme);
        if (!m.find()) {
            bad.add("README 里找不到「N 个 Controller」这条主张");
        } else if (c.controllers != Long.parseLong(m.group(1))) {
            bad.add("Controller 数对不上：README 写 " + m.group(1)
                    + " 个，z-oss-api/src/main 下 *Controller.java 实数是 " + c.controllers);
        }

        // 主张 7：「建 5 张表：`oss_user`、`oss_bucket`、…」
        // 清单只吃反引号包住的名字（列表末尾可能换行，别把下一句的路径也吞进来）
        m = Pattern.compile("建 (\\d+) 张表：((?:`[^`]+`[、\\s]*)+)").matcher(readme);
        if (!m.find()) {
            bad.add("README 里找不到「建 N 张表」这条主张");
        } else {
            Set<String> listedTables = backticked(m.group(2));
            if (c.tables != Integer.parseInt(m.group(1))) {
                bad.add("表数对不上：README 写 " + m.group(1)
                        + " 张，@TableName 唯一值实数是 " + c.tables);
            }
            if (listedTables.size() != c.tables) {
                bad.add("表数对不上：README 列出 " + listedTables.size() + " 张表（"
                        + String.join("、", listedTables) + "），@TableName 唯一值实数是 " + c.tables);
            } else if (!c.tableNameSet.containsAll(listedTables)) {
                bad.add("表名对不上：README 列了 " + String.join("、", listedTables)
                        + "，@TableName 锚定的实数是 " + String.join("、", c.tableNameSet));
            }
        }

        if (!bad.isEmpty()) {
            throw new AssertionError("README 的定量主张与盘面对不上（" + bad.size() + " 条）：\n  - "
                    + String.join("\n  - ", bad));
        }
    }

    /** 跑一遍核对，把它抛出的 AssertionError 消息取出来（没抛就返回空串）。 */
    private static String messageOf(String readme, Counts c) {
        try {
            verifyClaims(readme, c);
            return "";
        } catch (AssertionError e) {
            return e.getMessage() == null ? "" : e.getMessage();
        }
    }

    @Test
    @DisplayName("README 的定量主张与源码重算值逐条对账")
    void readmeClaimsMatchTheCode() {
        Path root = repoRoot();
        Counts c = recomputed();
        verifyClaims(read(root.resolve("README.md")), c);
    }

    @Test
    @DisplayName("尺子的牙口：把 README 的数改错，判定必须判红")
    void rulerActuallyCondemnsWrongNumbers() {
        String readme = read(repoRoot().resolve("README.md"));
        Counts c = recomputed();

        // 挑一条**本来就对得上**的主张来改：改之前它的报错消息里不该出现它
        String before = messageOf(readme, c);
        assertFalse(before.contains("Mapper 数对不上"),
                "改之前「Mapper 数」这条就不对上了（README " + before + "）—— 换一条本来对得上的主张来做变异");

        String tampered = readme.replaceFirst("3 个 Mapper", "4 个 Mapper");
        assertNotEquals(readme, tampered, "篡改失败：README 里找不到「3 个 Mapper」字样（写法变了？）");

        AssertionError err = assertThrows(AssertionError.class,
                () -> verifyClaims(tampered, c));
        assertTrue(err.getMessage().contains("Mapper 数对不上"), "报错要指向真正对不上的那条主张，实际是：" + err.getMessage());
    }

    @Test
    @DisplayName("不许冤枉健康页：真实 README 不得被判红")
    void healthyReadmeIsNotCondemned() {
        String readme = read(repoRoot().resolve("README.md"));
        verifyClaims(readme, recomputed());
    }
}
