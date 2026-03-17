package com.wewins.fota.module.system.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码生成工具
 * <p>
 * 用于生成用户密码的 BCrypt 哈希值
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
public class PasswordGeneratorTest {

    /**
     * 生成单个密码的 BCrypt 哈希
     * <p>
     * 修改此方法中的密码字符串，运行测试即可获得哈希值
     * </p>
     */
    @Test
    public void generatePasswordHash() {
        // 在这里修改密码
        String password = "wewins@2026";

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(password);

        System.out.println("==========================================");
        System.out.println("密码: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println("==========================================");

        // 验证密码
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + (matches ? "✓ 通过" : "✗ 失败"));
    }

    /**
     * 批量生成多个密码的 BCrypt 哈希
     * <p>
     * 在此方法中添加需要生成的密码列表
     * </p>
     */
    @Test
    public void generateMultiplePasswordHashes() {
        String[] passwords = {
                "wewins@2026",
                "admin123",
                "password123"
        };

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        System.out.println("\n==========================================");
        System.out.println("批量生成密码哈希");
        System.out.println("==========================================\n");

        for (String password : passwords) {
            String hash = encoder.encode(password);
            System.out.println("密码: " + password);
            System.out.println("哈希: " + hash);
            System.out.println("---");
        }

        System.out.println("==========================================\n");
    }

    /**
     * 验证密码是否匹配
     * <p>
     * 用于验证输入的密码是否与哈希匹配
     * </p>
     */
    @Test
    public void verifyPassword() {
        // 修改这里的密码和哈希进行验证
        String password = "wewins@2026";
        String hash = "$2a$10$Ozz0GI4/f/ySiHo9Dc2lr.y36nhc7k4S82k2b.L3ak63WciiMJ0ui"; // wewins@2026 的哈希

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean matches = encoder.matches(password, hash);

        System.out.println("==========================================");
        System.out.println("密码验证");
        System.out.println("==========================================");
        System.out.println("输入密码: " + password);
        System.out.println("存储哈希: " + hash);
        System.out.println("验证结果: " + (matches ? "✓ 匹配" : "✗ 不匹配"));
        System.out.println("==========================================");
    }

    /**
     * 命令行模式：通过运行时参数生成密码
     * <p>
     * 使用方法：
     * IDEA: Run -> Edit Configurations -> Program arguments: --password=your_password
     * Maven: mvn test -Dtest=PasswordGeneratorTest#generatePasswordWithArgs -Dpassword=your_password
     * </p>
     */
    @Test
    public void generatePasswordWithArgs() {
        String password = System.getProperty("password", "defaultPassword");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(password);

        System.out.println("==========================================");
        System.out.println("密码: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println();
        System.out.println("SQL INSERT 示例:");
        System.out.println("INSERT INTO sys_users (username, password_hash) VALUES ('user', '" + hash + "');");
        System.out.println("==========================================");
    }
}
