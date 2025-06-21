package ca.bazlur.eventsourcing.infrastructure.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultSnapshotStrategy, which determines when to create snapshots
 * based on the number of events since the last snapshot.
 */
class DefaultSnapshotStrategyTest {

    @Test
    void shouldUseDefaultFrequencyWhenNotConfigured() {
        // Given
        var strategy = new DefaultSnapshotStrategy(0); // 0 will trigger default value

        // When
        var frequency = strategy.getSnapshotFrequency();

        // Then
        assertEquals(100, frequency, "Should use default frequency (100) when configured with 0");
    }

    @Test
    void shouldUseConfiguredFrequency() {
        // Given
        var strategy = new DefaultSnapshotStrategy(50);

        // When
        var frequency = strategy.getSnapshotFrequency();

        // Then
        assertEquals(50, frequency, "Should use the configured frequency value");
    }

    @Test
    void shouldHandleNegativeFrequency() {
        // Given/When/Then
        assertThrows(IllegalArgumentException.class, () -> new DefaultSnapshotStrategy(-1),
            "Should not accept negative frequency values");
    }

    @ParameterizedTest(name = "frequency={0}, currentVersion={1}, lastSnapshotVersion={2} => shouldCreate={3}")
    @CsvSource(value = {
        // First snapshot scenarios
        "100, 0, NULL, false",      // Not enough events for first snapshot
        "100, 99, NULL, false",     // One event short of first snapshot
        "100, 100, NULL, true",     // Exactly enough events for first snapshot
        "100, 150, NULL, true",     // More than enough events for first snapshot

        // Subsequent snapshot scenarios
        "100, 150, 100, false",     // Not enough events since last snapshot
        "100, 199, 100, false",     // Almost enough events since last snapshot
        "100, 200, 100, true",      // Exactly enough events since last snapshot
        "100, 250, 100, true",      // More than enough events since last snapshot

        // Edge cases
        "1, 1, NULL, true",         // Minimum frequency
        "100, 1000, 900, true",     // Large version numbers
        "100, 150, 149, false"      // Very recent snapshot
    }, nullValues = "NULL")
    void shouldCreateSnapshotBasedOnFrequencyAndVersion(
            int frequency, long currentVersion, Long lastSnapshotVersion, boolean expectedResult) {
        // Given
        var strategy = new DefaultSnapshotStrategy(frequency);
        var aggregateId = "test-aggregate";
        var aggregateType = "TestAggregate";

        // When
        var result = strategy.shouldCreateSnapshot(
            aggregateId, aggregateType, currentVersion, lastSnapshotVersion);

        // Then
        assertEquals(expectedResult, result,
            String.format("Snapshot creation decision failed for frequency=%d, currentVersion=%d, lastSnapshotVersion=%s",
                frequency, currentVersion, lastSnapshotVersion));
    }

    @Test
    void shouldHandleMaximumVersionValues() {
        // Given
        var strategy = new DefaultSnapshotStrategy(100);
        var aggregateId = "test-aggregate";
        var aggregateType = "TestAggregate";
        var currentVersion = Long.MAX_VALUE;
        var lastSnapshotVersion = Long.MAX_VALUE - 50;

        // When
        var result = strategy.shouldCreateSnapshot(
            aggregateId, aggregateType, currentVersion, lastSnapshotVersion);

        // Then
        assertTrue(result, "Should handle maximum version values correctly");
    }

    @Test
    void shouldHandleVersionOverflow() {
        // Given
        var strategy = new DefaultSnapshotStrategy(100);
        var aggregateId = "test-aggregate";
        var aggregateType = "TestAggregate";
        var currentVersion = Long.MAX_VALUE;
        var lastSnapshotVersion = 0L;

        // When/Then
        assertDoesNotThrow(() -> 
            strategy.shouldCreateSnapshot(aggregateId, aggregateType, currentVersion, lastSnapshotVersion),
            "Should handle potential version overflow gracefully");
    }
}
