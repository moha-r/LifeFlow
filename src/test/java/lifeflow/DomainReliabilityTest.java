package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityReason;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.service.DataValidator;
import lifeflow.service.DonationPolicy;
import org.junit.jupiter.api.Test;

final class DomainReliabilityTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Test
    void eligibilityExplainsTheWaitingPeriodAndNextDate() {
        Donor donor = new Donor("D1", "Aisha", 25, 55.0, BloodType.A_POS,
                LocalDate.of(2026, 8, 6));

        DonationPolicy policy = new DonationPolicy(Clock.fixed(
                Instant.parse("2026-08-18T04:00:00Z"), ZoneOffset.UTC));
        var result = policy.evaluate(donor, TODAY,
                donor.getExternalLastDonationDate());

        assertFalse(result.eligible());
        assertEquals(EligibilityReason.WAITING_PERIOD, result.reason());
        assertEquals(LocalDate.of(2026, 11, 6), result.nextEligibleDate());
        assertTrue(result.message().contains("2026-11-06"));
    }

    @Test
    void futureAndExpiredUnitsAreNotAvailable() {
        BloodUnit future = unit("U1", TODAY.plusDays(1), TODAY.plusDays(10),
                UnitStatus.AVAILABLE);
        BloodUnit expired = unit("U2", TODAY.minusDays(10), TODAY.minusDays(1),
                UnitStatus.AVAILABLE);

        assertFalse(future.isAvailable(TODAY));
        assertFalse(expired.isAvailable(TODAY));
        assertTrue(expired.isExpired(TODAY));
    }

    @Test
    void validatorRejectsOrphanUnitsAndDuplicateExternalDonationHistory() {
        Donor donor = new Donor("D1", "Aisha", 25, 55.0, BloodType.A_POS,
                TODAY.minusDays(2));
        BloodUnit linked = unit("U1", TODAY.minusDays(2), TODAY.plusDays(30),
                UnitStatus.AVAILABLE);
        BloodUnit orphan = new BloodUnit("U2", "MISSING", BloodType.A_POS,
                TODAY.minusDays(2), TODAY.plusDays(30), UnitStatus.AVAILABLE);

        LifeFlowState stale = state(List.of(donor), List.of(linked), List.of(), List.of());
        LifeFlowState brokenLink = state(List.of(donor), List.of(orphan), List.of(), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> DataValidator.validate(stale, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> DataValidator.validate(brokenLink, TODAY));
    }

    @Test
    void fulfilledRequestsAndUsedUnitsRequireOneAuditRecord() {
        Donor donor = new Donor("D1", "Aisha", 25, 55.0, BloodType.A_POS,
                null);
        BloodUnit used = unit("U1", TODAY.minusDays(2), TODAY.plusDays(30),
                UnitStatus.USED);
        BloodRequest request = new RegularRequest("R1", "Clinic", BloodType.A_POS,
                1, TODAY.minusDays(1), RequestStatus.FULFILLED);
        LifeFlowState withoutAudit = state(List.of(donor), List.of(used),
                List.of(request), List.of());
        FulfilmentRecord audit = new FulfilmentRecord("R1", TODAY, List.of("U1"));
        LifeFlowState complete = state(List.of(donor), List.of(used),
                List.of(request), List.of(audit));

        assertThrows(IllegalArgumentException.class,
                () -> DataValidator.validate(withoutAudit, TODAY));
        DataValidator.validate(complete, TODAY);
    }

    @Test
    void auditMustUseAUnitThatWasValidAfterTheRequest() {
        Donor donor = new Donor("D1", "Aisha", 25, 55.0, BloodType.A_POS,
                null);
        BloodUnit used = unit("U1", TODAY.minusDays(10), TODAY.minusDays(2),
                UnitStatus.USED);
        BloodRequest request = new RegularRequest("R1", "Clinic", BloodType.A_POS,
                1, TODAY.minusDays(5), RequestStatus.FULFILLED);
        FulfilmentRecord beforeRequest = new FulfilmentRecord("R1",
                TODAY.minusDays(6), List.of("U1"));
        FulfilmentRecord afterExpiry = new FulfilmentRecord("R1",
                TODAY.minusDays(1), List.of("U1"));

        assertThrows(IllegalArgumentException.class, () -> DataValidator.validate(
                state(List.of(donor), List.of(used), List.of(request),
                        List.of(beforeRequest)), TODAY));
        assertThrows(IllegalArgumentException.class, () -> DataValidator.validate(
                state(List.of(donor), List.of(used), List.of(request),
                        List.of(afterExpiry)), TODAY));
    }

    private static BloodUnit unit(String id, LocalDate donation, LocalDate expiry,
                                  UnitStatus status) {
        return new BloodUnit(id, "D1", BloodType.A_POS, donation, expiry, status);
    }

    private static LifeFlowState state(List<Donor> donors, List<BloodUnit> units,
                                       List<BloodRequest> requests,
                                       List<FulfilmentRecord> fulfilments) {
        return new LifeFlowState(0, new ArrayList<>(donors), new ArrayList<>(units),
                new ArrayList<>(requests), new ArrayList<>(fulfilments));
    }
}
