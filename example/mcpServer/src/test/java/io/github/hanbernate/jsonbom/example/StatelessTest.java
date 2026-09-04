package io.github.hanbernate.jsonbom.example;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("stateless")
public class StatelessTest extends BaseMcpClientTest{

    @Test
    public void test(){
        super.test();
    }
    
}
