package lifeflow;

public final class AllTests {
    private AllTests() {
    }

    public static void main(String[] args) throws Exception {
        DonorTests.run();
        BloodUnitTests.run();
        BloodRequestTests.run();
        InventoryTests.run();
        MatchingServiceTests.run();
        LifeFlowControllerTests.run();
        ModernUiTests.run();
        System.out.println("All tests passed.");
    }
}
