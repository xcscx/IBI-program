package com.xtx.springbootinit;

import com.xtx.springbootinit.model.enums.UserRoleEnum;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 主类测试
 *
 * @author IT蛋
 */
@SpringBootTest
class MainApplicationTests {

    UserRoleEnum a = UserRoleEnum.ADMIN;
    UserRoleEnum b = UserRoleEnum.ADMIN;

    @Test
    void contextLoads() {
        System.out.println(a.equals(b));
    }

}
