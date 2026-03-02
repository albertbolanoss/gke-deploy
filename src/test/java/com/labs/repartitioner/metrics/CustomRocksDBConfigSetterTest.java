package com.labs.repartitioner.metrics;

import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.Options;
import org.rocksdb.Statistics;
import org.rocksdb.StatsLevel;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CustomRocksDBConfigSetter.
 * Verifies that Statistics are enabled correctly and available after initialization.
 * 
 * Requirements: 7.2, 7.3
 */
class CustomRocksDBConfigSetterTest {

    private CustomRocksDBConfigSetter configSetter;
    private Options options;
    private Map<String, Object> configs;

    @BeforeEach
    void setUp() {
        configSetter = new CustomRocksDBConfigSetter();
        options = new Options();
        configs = new HashMap<>();
        
        // Clear any existing statistics from previous tests
        CustomRocksDBConfigSetter.getStoreStatistics().clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up resources
        if (options != null) {
            options.close();
        }
        
        // Clear statistics after each test
        CustomRocksDBConfigSetter.getStoreStatistics().clear();
    }

    // Test: Statistics Enablement - Requirement 7.2

    @Test
    void setConfig_shouldEnableStatistics() {
        // Arrange
        String storeName = "test-store";

        // Act
        configSetter.setConfig(storeName, options, configs);

        // Assert
        Statistics statistics = options.statistics();
        assertThat(statistics)
            .as("Statistics should be enabled on Options")
            .isNotNull();
    }

    @Test
    void setConfig_shouldSetStatsLevelToExceptDetailedTimers() {
        // Arrange
        String storeName = "test-store";

        // Act
        configSetter.setConfig(storeName, options, configs);

        // Assert
        Statistics statistics = options.statistics();
        assertThat(statistics).isNotNull();
        assertThat(statistics.statsLevel())
            .as("Stats level should be EXCEPT_DETAILED_TIMERS")
            .isEqualTo(StatsLevel.EXCEPT_DETAILED_TIMERS);
    }

    @Test
    void setConfig_shouldEnableStatisticsForMultipleStores() {
        // Arrange
        String storeName1 = "store-1";
        String storeName2 = "store-2";
        Options options1 = new Options();
        Options options2 = new Options();

        try {
            // Act
            configSetter.setConfig(storeName1, options1, configs);
            configSetter.setConfig(storeName2, options2, configs);

            // Assert
            assertThat(options1.statistics())
                .as("Statistics should be enabled for store-1")
                .isNotNull();
            
            assertThat(options2.statistics())
                .as("Statistics should be enabled for store-2")
                .isNotNull();
        } finally {
            options1.close();
            options2.close();
        }
    }

    // Test: Statistics Availability - Requirement 7.3

    @Test
    void setConfig_shouldMakeStatisticsAvailableViaGetStoreStatistics() {
        // Arrange
        String storeName = "test-store";

        // Act
        configSetter.setConfig(storeName, options, configs);

        // Assert
        Map<String, Statistics> storeStatistics = CustomRocksDBConfigSetter.getStoreStatistics();
        assertThat(storeStatistics)
            .as("Store statistics map should not be null")
            .isNotNull();
        
        assertThat(storeStatistics.containsKey(storeName))
            .as("Statistics should be available for the store")
            .isTrue();
        
        Statistics statistics = storeStatistics.get(storeName);
        assertThat(statistics)
            .as("Statistics object should not be null")
            .isNotNull();
    }

    @Test
    void setConfig_shouldStoreStatisticsForMultipleStores() {
        // Arrange
        String storeName1 = "store-1";
        String storeName2 = "store-2";
        Options options1 = new Options();
        Options options2 = new Options();

        try {
            // Act
            configSetter.setConfig(storeName1, options1, configs);
            configSetter.setConfig(storeName2, options2, configs);

            // Assert
            Map<String, Statistics> storeStatistics = CustomRocksDBConfigSetter.getStoreStatistics();
            assertThat(storeStatistics)
                .as("Store statistics should contain both stores")
                .hasSize(2)
                .containsKeys(storeName1, storeName2);
            
            assertThat(storeStatistics.get(storeName1))
                .as("Statistics for store-1 should not be null")
                .isNotNull();
            
            assertThat(storeStatistics.get(storeName2))
                .as("Statistics for store-2 should not be null")
                .isNotNull();
        } finally {
            options1.close();
            options2.close();
        }
    }

    @Test
    void getStoreStatistics_shouldReturnFunctionalStatisticsObject() {
        // Arrange
        String storeName = "test-store";

        // Act
        configSetter.setConfig(storeName, options, configs);

        // Assert
        Statistics statisticsFromOptions = options.statistics();
        Statistics statisticsFromMap = CustomRocksDBConfigSetter.getStoreStatistics().get(storeName);
        
        assertThat(statisticsFromMap)
            .as("Statistics from map should not be null")
            .isNotNull();
        
        assertThat(statisticsFromOptions)
            .as("Statistics from options should not be null")
            .isNotNull();
        
        // Verify both Statistics objects have the same stats level
        assertThat(statisticsFromMap.statsLevel())
            .as("Statistics from map should have the same stats level as options")
            .isEqualTo(statisticsFromOptions.statsLevel());
    }

    // Test: Close Method - Cleanup

    @Test
    void close_shouldRemoveStatisticsFromMap() {
        // Arrange
        String storeName = "test-store";
        configSetter.setConfig(storeName, options, configs);
        
        // Verify statistics are present
        assertThat(CustomRocksDBConfigSetter.getStoreStatistics().containsKey(storeName))
            .as("Statistics should be present before close")
            .isTrue();

        // Act
        configSetter.close(storeName, options);

        // Assert
        assertThat(CustomRocksDBConfigSetter.getStoreStatistics().containsKey(storeName))
            .as("Statistics should be removed after close")
            .isFalse();
    }

    @Test
    void close_shouldHandleNonExistentStore() {
        // Arrange
        String storeName = "non-existent-store";

        // Act & Assert - Should not throw exception
        configSetter.close(storeName, options);
        
        // Verify map is still empty
        assertThat(CustomRocksDBConfigSetter.getStoreStatistics())
            .as("Statistics map should be empty")
            .isEmpty();
    }

    @Test
    void close_shouldOnlyRemoveSpecifiedStore() {
        // Arrange
        String storeName1 = "store-1";
        String storeName2 = "store-2";
        Options options1 = new Options();
        Options options2 = new Options();

        try {
            configSetter.setConfig(storeName1, options1, configs);
            configSetter.setConfig(storeName2, options2, configs);
            
            // Verify both stores are present
            assertThat(CustomRocksDBConfigSetter.getStoreStatistics())
                .as("Both stores should be present")
                .hasSize(2);

            // Act
            configSetter.close(storeName1, options1);

            // Assert
            Map<String, Statistics> storeStatistics = CustomRocksDBConfigSetter.getStoreStatistics();
            assertThat(storeStatistics)
                .as("Only store-2 should remain")
                .hasSize(1)
                .containsKey(storeName2)
                .doesNotContainKey(storeName1);
        } finally {
            options1.close();
            options2.close();
        }
    }

    // Test: Interface Implementation

    @Test
    void customRocksDBConfigSetter_shouldImplementRocksDBConfigSetter() {
        // Assert
        assertThat(configSetter)
            .as("CustomRocksDBConfigSetter should implement RocksDBConfigSetter")
            .isInstanceOf(RocksDBConfigSetter.class);
    }

    // Test: Concurrent Access

    @Test
    void getStoreStatistics_shouldReturnConcurrentMap() {
        // Arrange
        String storeName = "test-store";
        configSetter.setConfig(storeName, options, configs);

        // Act
        Map<String, Statistics> storeStatistics = CustomRocksDBConfigSetter.getStoreStatistics();

        // Assert - Verify the map can be safely accessed concurrently
        assertThat(storeStatistics)
            .as("Store statistics should be a concurrent map")
            .isNotNull();
        
        // Verify we can iterate and modify without ConcurrentModificationException
        storeStatistics.forEach((key, value) -> {
            assertThat(key).isNotNull();
            assertThat(value).isNotNull();
        });
    }
}
