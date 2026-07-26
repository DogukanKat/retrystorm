package retrystorm.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import retrystorm.scenario.CanonicalExperiment.NamedPolicy;

class CanonicalExperimentTest {

    @Test
    void scenarioIsInternallyConsistent() {
        Scenario scenario = CanonicalExperiment.scenario();
        assertTrue(scenario.spikeStartMicros() < scenario.spikeEndMicros());
        assertTrue(scenario.spikeEndMicros() < scenario.horizonMicros());
        assertTrue(scenario.spikeRatePerSecond() > scenario.baseRatePerSecond());
    }

    @Test
    void offersAllSixDistinctPoliciesWithFreshInstances() {
        List<NamedPolicy> policies = CanonicalExperiment.policies();
        assertEquals(6, policies.size());
        assertEquals(6, policies.stream().map(NamedPolicy::name).distinct().count());
        for (NamedPolicy policy : policies) {
            assertNotNull(policy.factory().get());
            assertTrue(policy.factory().get() != policy.factory().get(), "each call yields a new instance");
        }
    }

    @Test
    void everyPolicyRunsAndFillsAllBuckets() {
        Scenario scenario = CanonicalExperiment.scenario();
        int expectedBuckets = (int) (scenario.horizonMicros() / scenario.bucketMicros());
        for (NamedPolicy policy : CanonicalExperiment.policies()) {
            List<retrystorm.metrics.BucketRow> rows = ScenarioRunner.run(scenario, policy.factory().get());
            assertEquals(expectedBuckets, rows.size(), policy.name() + " bucket count");
        }
    }
}
