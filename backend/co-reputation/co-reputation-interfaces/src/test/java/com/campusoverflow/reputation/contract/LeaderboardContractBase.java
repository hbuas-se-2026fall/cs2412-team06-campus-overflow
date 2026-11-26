package com.campusoverflow.reputation.contract;

import com.campusoverflow.reputation.application.LeaderboardEntryView;
import com.campusoverflow.reputation.application.LeaderboardService;
import com.campusoverflow.reputation.interfaces.LeaderboardController;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 排行榜契约测试基类：standaloneSetup 装配控制器与 mock 服务，不启动 Spring 容器、不依赖 Docker。
 * 由 spring-cloud-contract-maven-plugin 生成的 provider 测试继承本类，复用同一份固定排行榜条目。
 */
public abstract class LeaderboardContractBase {

    @BeforeEach
    void setupMockMvc() {
        LeaderboardService service = Mockito.mock(LeaderboardService.class);
        Mockito.when(service.top(Mockito.any(), Mockito.anyInt())).thenReturn(List.of(
                new LeaderboardEntryView(1, 101L, "小明", true, 250L),
                new LeaderboardEntryView(2, 102L, "小红", false, 180L)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LeaderboardController(service))
                .setControllerAdvice(new ContractTestExceptionAdvice())
                .build();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }
}
