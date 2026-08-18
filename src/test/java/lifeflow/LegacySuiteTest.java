package lifeflow;

import org.junit.jupiter.api.Test;

final class LegacySuiteTest {
    @Test
    void existingBehaviourRemainsCovered() throws Exception {
        AllTests.main(new String[0]);
    }
}
