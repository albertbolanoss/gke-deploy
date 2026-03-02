# Metrics Documentation

## Overview

This document provides comprehensive documentation for all metrics exposed by the LABS_GKE_DEPLOY application. Metrics are collected using Micrometer and exposed via Spring Boot Actuator in Prometheus format for consumption by Dynatrace OneAgent.

## Accessing Metrics

### Local Development

- **All Metrics List**: `http://localhost:8080/actuator/metrics`
- **Specific Metric**: `http://localhost:8080/actuator/metrics/{metric.name}`
- **Prometheus Format**: `http://localhost:8080/actuator/prometheus`

### Production (GKE)

Dynatrace OneAgent automatically scrapes metrics from `/actuator/prometheus` endpoint.

## Common Tags

All metrics include these common tags for filtering and grouping:

| Tag | Description | Example Value |
|-----|-------------|---------------|
| `application` | Application name | `LABS_GKE_DEPLOY` |
| `environment` | Deployment environment | `dev`, `staging`, `production` |
| `host` | Hostname of the instance | `pod-abc123` |

---

## JVM Metrics

### Memory Metrics

#### `jvm.memory.used`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Current memory usage
- **Tags**:
  - `area`: `heap` or `nonheap`
  - `id`: Memory pool name (e.g., `G1 Old Gen`, `G1 Eden Space`, `Metaspace`)
- **Example**: `jvm.memory.used{area="heap",id="G1 Old Gen"} 524288000`
- **Alert Threshold**: > 85% of `jvm.memory.max`

#### `jvm.memory.max`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Maximum memory that can be used
- **Tags**:
  - `area`: `heap` or `nonheap`
  - `id`: Memory pool name
- **Example**: `jvm.memory.max{area="heap",id="G1 Old Gen"} 2147483648`

#### `jvm.memory.committed`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Memory guaranteed to be available
- **Tags**:
  - `area`: `heap` or `nonheap`
  - `id`: Memory pool name
- **Example**: `jvm.memory.committed{area="heap",id="G1 Old Gen"} 1073741824`

### Garbage Collection Metrics

#### `jvm.gc.pause`
- **Type**: Timer
- **Unit**: Seconds
- **Description**: Time spent in GC pauses
- **Tags**:
  - `action`: GC action (e.g., `end of minor GC`, `end of major GC`)
  - `cause`: GC cause (e.g., `Allocation Failure`, `G1 Evacuation Pause`)
- **Example**: `jvm.gc.pause{action="end of minor GC",cause="Allocation Failure"} 0.015`
- **Alert Threshold**: p99 > 100ms or frequency > 10/min

#### `jvm.gc.memory.allocated`
- **Type**: Counter
- **Unit**: Bytes
- **Description**: Total bytes allocated in heap memory
- **Example**: `jvm.gc.memory.allocated 10737418240`

#### `jvm.gc.memory.promoted`
- **Type**: Counter
- **Unit**: Bytes
- **Description**: Bytes promoted from young to old generation
- **Example**: `jvm.gc.memory.promoted 524288000`
- **Alert Threshold**: High promotion rate may indicate memory pressure

### Thread Metrics

#### `jvm.threads.live`
- **Type**: Gauge
- **Unit**: Threads
- **Description**: Current number of live threads
- **Example**: `jvm.threads.live 42`
- **Alert Threshold**: Sudden increase may indicate thread leak

#### `jvm.threads.daemon`
- **Type**: Gauge
- **Unit**: Threads
- **Description**: Current number of daemon threads
- **Example**: `jvm.threads.daemon 38`

#### `jvm.threads.peak`
- **Type**: Gauge
- **Unit**: Threads
- **Description**: Peak number of live threads since JVM start
- **Example**: `jvm.threads.peak 50`

#### `jvm.threads.states`
- **Type**: Gauge
- **Unit**: Threads
- **Description**: Number of threads in each state
- **Tags**:
  - `state`: `runnable`, `blocked`, `waiting`, `timed-waiting`, `new`, `terminated`
- **Example**: `jvm.threads.states{state="runnable"} 8`
- **Alert Threshold**: High `blocked` or `waiting` counts may indicate contention

### CPU Metrics

#### `system.cpu.usage`
- **Type**: Gauge
- **Unit**: Percentage (0.0 to 1.0)
- **Description**: System-wide CPU usage
- **Example**: `system.cpu.usage 0.45`
- **Alert Threshold**: > 0.80 (80%)

#### `process.cpu.usage`
- **Type**: Gauge
- **Unit**: Percentage (0.0 to 1.0)
- **Description**: CPU usage by this JVM process
- **Example**: `process.cpu.usage 0.35`
- **Alert Threshold**: > 0.70 (70%)

### Class Loader Metrics

#### `jvm.classes.loaded`
- **Type**: Gauge
- **Unit**: Classes
- **Description**: Number of classes currently loaded
- **Example**: `jvm.classes.loaded 12543`

#### `jvm.classes.unloaded`
- **Type**: Counter
- **Unit**: Classes
- **Description**: Total number of classes unloaded since JVM start
- **Example**: `jvm.classes.unloaded 125`

---

## Kafka Streams Metrics

### Throughput Metrics

#### `kafka.streams.records.consumed.rate`
- **Type**: Gauge
- **Unit**: Records per second
- **Description**: Rate of records consumed from Kafka topics
- **Tags**:
  - `topic`: Source topic name
  - `partition`: Partition number
  - `task-id`: Kafka Streams task identifier
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.records.consumed.rate{topic="uppercase",partition="0",task-id="0_0"} 1500.5`
- **Alert Threshold**: Sudden drop may indicate upstream issues

#### `kafka.streams.records.produced.rate`
- **Type**: Gauge
- **Unit**: Records per second
- **Description**: Rate of records produced to Kafka topics
- **Tags**:
  - `topic`: Destination topic name
  - `partition`: Partition number
  - `task-id`: Kafka Streams task identifier
- **Example**: `kafka.streams.records.produced.rate{topic="uppercase-repartition",partition="1"} 1480.2`

#### `kafka.streams.bytes.consumed.rate`
- **Type**: Gauge
- **Unit**: Bytes per second
- **Description**: Rate of bytes consumed from Kafka topics
- **Tags**:
  - `topic`: Source topic name
  - `partition`: Partition number
- **Example**: `kafka.streams.bytes.consumed.rate{topic="uppercase"} 45678.9`

#### `kafka.streams.bytes.produced.rate`
- **Type**: Gauge
- **Unit**: Bytes per second
- **Description**: Rate of bytes produced to Kafka topics
- **Tags**:
  - `topic`: Destination topic name
- **Example**: `kafka.streams.bytes.produced.rate{topic="uppercase-repartition"} 43210.5`

### Latency Metrics

#### `kafka.streams.process.latency.avg`
- **Type**: Gauge
- **Unit**: Milliseconds
- **Description**: Average processing latency per record
- **Tags**:
  - `task-id`: Kafka Streams task identifier
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.process.latency.avg{task-id="0_0",thread-id="StreamThread-1"} 12.5`
- **Alert Threshold**: > 100ms may indicate processing bottleneck

#### `kafka.streams.process.latency.max`
- **Type**: Gauge
- **Unit**: Milliseconds
- **Description**: Maximum processing latency per record
- **Tags**:
  - `task-id`: Kafka Streams task identifier
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.process.latency.max{task-id="0_0"} 250.0`
- **Alert Threshold**: > 500ms indicates severe processing delays

#### `kafka.streams.commit.latency.avg`
- **Type**: Gauge
- **Unit**: Milliseconds
- **Description**: Average time to commit offsets
- **Tags**:
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.commit.latency.avg{thread-id="StreamThread-1"} 45.2`
- **Alert Threshold**: > 200ms may indicate broker issues

#### `kafka.streams.commit.latency.max`
- **Type**: Gauge
- **Unit**: Milliseconds
- **Description**: Maximum time to commit offsets
- **Tags**:
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.commit.latency.max{thread-id="StreamThread-1"} 500.0`

### Consumer Lag Metrics

#### `kafka.streams.records.lag`
- **Type**: Gauge
- **Unit**: Records
- **Description**: Current consumer lag (records behind)
- **Tags**:
  - `topic`: Source topic name
  - `partition`: Partition number
  - `task-id`: Kafka Streams task identifier
- **Example**: `kafka.streams.records.lag{topic="uppercase",partition="0",task-id="0_0"} 1250`
- **Alert Threshold**: > 10000 indicates significant lag

#### `kafka.streams.records.lag.max`
- **Type**: Gauge
- **Unit**: Records
- **Description**: Maximum consumer lag across all partitions
- **Tags**:
  - `topic`: Source topic name
- **Example**: `kafka.streams.records.lag.max{topic="uppercase"} 2500`
- **Alert Threshold**: > 50000 indicates critical lag

### Thread State Metrics

#### `kafka.streams.thread.state`
- **Type**: Gauge
- **Unit**: Enumeration (0=DEAD, 1=RUNNING, 2=REBALANCING, 3=PENDING_SHUTDOWN)
- **Description**: Current state of stream thread
- **Tags**:
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.thread.state{thread-id="StreamThread-1"} 1`
- **Alert Threshold**: State 0 (DEAD) requires immediate attention

#### `kafka.streams.active.tasks`
- **Type**: Gauge
- **Unit**: Tasks
- **Description**: Number of active tasks assigned to thread
- **Tags**:
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.active.tasks{thread-id="StreamThread-1"} 4`

#### `kafka.streams.standby.tasks`
- **Type**: Gauge
- **Unit**: Tasks
- **Description**: Number of standby tasks assigned to thread
- **Tags**:
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.standby.tasks{thread-id="StreamThread-1"} 2`

### Rebalancing Metrics

#### `kafka.streams.rebalance.latency.avg`
- **Type**: Gauge
- **Unit**: Milliseconds
- **Description**: Average time spent in rebalancing
- **Tags**:
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.rebalance.latency.avg{thread-id="StreamThread-1"} 3500.0`
- **Alert Threshold**: > 10000ms (10s) indicates slow rebalancing

#### `kafka.streams.rebalance.latency.max`
- **Type**: Gauge
- **Unit**: Milliseconds
- **Description**: Maximum time spent in rebalancing
- **Tags**:
  - `thread-id`: Stream thread identifier
- **Example**: `kafka.streams.rebalance.latency.max{thread-id="StreamThread-1"} 15000.0`

### Repartitioning Metrics

#### `kafka.streams.repartition.records.sent`
- **Type**: Counter
- **Unit**: Records
- **Description**: Total records sent during repartitioning operations
- **Tags**:
  - `node-id`: Topology node identifier
  - `task-id`: Kafka Streams task identifier
- **Example**: `kafka.streams.repartition.records.sent{node-id="KSTREAM-KEY-SELECT-0000000001",task-id="0_0"} 125000`

#### `kafka.streams.repartition.records.dropped`
- **Type**: Counter
- **Unit**: Records
- **Description**: Total records dropped during repartitioning
- **Tags**:
  - `node-id`: Topology node identifier
  - `task-id`: Kafka Streams task identifier
- **Example**: `kafka.streams.repartition.records.dropped{node-id="KSTREAM-KEY-SELECT-0000000001"} 0`
- **Alert Threshold**: > 0 indicates data loss in repartitioning

---

## RocksDB Metrics

### Memory Metrics

#### `rocksdb.memtable.size.all`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Total size of all MemTables (active and immutable)
- **Tags**:
  - `state-store`: State store name
  - `column-family`: Column family name (usually `default`)
- **Example**: `rocksdb.memtable.size.all{state-store="uppercase-storage",column-family="default"} 8388608`
- **Alert Threshold**: > 128MB may indicate flush delays

#### `rocksdb.memtable.size.unflushed`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Size of unflushed MemTables
- **Tags**:
  - `state-store`: State store name
  - `column-family`: Column family name
- **Example**: `rocksdb.memtable.size.unflushed{state-store="uppercase-storage"} 4194304`

#### `rocksdb.block.cache.usage`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Current block cache usage
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.block.cache.usage{state-store="uppercase-storage"} 16777216`
- **Alert Threshold**: Near capacity may cause cache thrashing

#### `rocksdb.block.cache.capacity`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Total block cache capacity
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.block.cache.capacity{state-store="uppercase-storage"} 33554432`

### Storage Metrics

#### `rocksdb.total.sst.files.size`
- **Type**: Gauge
- **Unit**: Bytes
- **Description**: Total size of all SST files
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.total.sst.files.size{state-store="uppercase-storage"} 524288000`
- **Alert Threshold**: Monitor growth rate for capacity planning

#### `rocksdb.num.live.sst.files`
- **Type**: Gauge
- **Unit**: Files
- **Description**: Number of active SST files
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.num.live.sst.files{state-store="uppercase-storage"} 15`
- **Alert Threshold**: > 100 may indicate compaction issues

### Operation Metrics

#### `rocksdb.get.count`
- **Type**: Counter
- **Unit**: Operations
- **Description**: Total number of Get operations
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.get.count{state-store="uppercase-storage"} 1250000`

#### `rocksdb.multiget.count`
- **Type**: Counter
- **Unit**: Operations
- **Description**: Total number of MultiGet operations
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.multiget.count{state-store="uppercase-storage"} 5000`

#### `rocksdb.put.count`
- **Type**: Counter
- **Unit**: Operations
- **Description**: Total number of Put operations
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.put.count{state-store="uppercase-storage"} 850000`

#### `rocksdb.delete.count`
- **Type**: Counter
- **Unit**: Operations
- **Description**: Total number of Delete operations
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.delete.count{state-store="uppercase-storage"} 12000`

#### `rocksdb.write.count`
- **Type**: Counter
- **Unit**: Operations
- **Description**: Total number of Write operations (batched writes)
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.write.count{state-store="uppercase-storage"} 425000`

### Latency Metrics

#### `rocksdb.get.latency.p50`
- **Type**: Gauge
- **Unit**: Microseconds
- **Description**: 50th percentile (median) Get operation latency
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.get.latency.p50{state-store="uppercase-storage"} 15.5`
- **Alert Threshold**: > 1000µs (1ms) may indicate performance issues

#### `rocksdb.get.latency.p99`
- **Type**: Gauge
- **Unit**: Microseconds
- **Description**: 99th percentile Get operation latency
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.get.latency.p99{state-store="uppercase-storage"} 250.0`
- **Alert Threshold**: > 10000µs (10ms) indicates severe slowdown

#### `rocksdb.write.latency.p50`
- **Type**: Gauge
- **Unit**: Microseconds
- **Description**: 50th percentile Write operation latency
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.write.latency.p50{state-store="uppercase-storage"} 45.2`
- **Alert Threshold**: > 5000µs (5ms) may indicate I/O bottleneck

#### `rocksdb.write.latency.p99`
- **Type**: Gauge
- **Unit**: Microseconds
- **Description**: 99th percentile Write operation latency
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.write.latency.p99{state-store="uppercase-storage"} 500.0`
- **Alert Threshold**: > 50000µs (50ms) indicates critical slowdown

### Throughput Metrics

#### `rocksdb.bytes.read`
- **Type**: Counter
- **Unit**: Bytes
- **Description**: Total bytes read from RocksDB
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.bytes.read{state-store="uppercase-storage"} 10737418240`

#### `rocksdb.bytes.written`
- **Type**: Counter
- **Unit**: Bytes
- **Description**: Total bytes written to RocksDB
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.bytes.written{state-store="uppercase-storage"} 5368709120`

### Compaction Metrics

#### `rocksdb.num.running.compactions`
- **Type**: Gauge
- **Unit**: Operations
- **Description**: Number of compactions currently running
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.num.running.compactions{state-store="uppercase-storage"} 2`
- **Alert Threshold**: Consistently > 0 may indicate write pressure

#### `rocksdb.num.pending.flushes`
- **Type**: Gauge
- **Unit**: Operations
- **Description**: Number of pending flush operations
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.num.pending.flushes{state-store="uppercase-storage"} 0`
- **Alert Threshold**: > 5 indicates MemTable backlog

#### `rocksdb.compact.write.bytes`
- **Type**: Counter
- **Unit**: Bytes
- **Description**: Total bytes written during compaction
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.compact.write.bytes{state-store="uppercase-storage"} 2147483648`

#### `rocksdb.compact.read.bytes`
- **Type**: Counter
- **Unit**: Bytes
- **Description**: Total bytes read during compaction
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.compact.read.bytes{state-store="uppercase-storage"} 3221225472`

#### `rocksdb.compaction.time.micros`
- **Type**: Counter
- **Unit**: Microseconds
- **Description**: Total time spent in compaction operations
- **Tags**:
  - `state-store`: State store name
- **Example**: `rocksdb.compaction.time.micros{state-store="uppercase-storage"} 45000000`

---

## Dynatrace Query Examples

### JVM Health Check
```
jvm.memory.used{area="heap"} / jvm.memory.max{area="heap"} > 0.85
```
Alert when heap usage exceeds 85%.

### Kafka Streams Processing Rate
```
sum(kafka.streams.records.consumed.rate{application="LABS_GKE_DEPLOY"})
```
Total records processed per second across all partitions.

### Consumer Lag Monitoring
```
max(kafka.streams.records.lag.max{application="LABS_GKE_DEPLOY",environment="production"})
```
Maximum lag across all topics and partitions.

### RocksDB Read Performance
```
rocksdb.get.latency.p99{state-store="uppercase-storage"} > 10000
```
Alert when p99 read latency exceeds 10ms.

### Thread State Monitoring
```
kafka.streams.thread.state{thread-id=~".*"} == 0
```
Detect dead stream threads.

### GC Pressure
```
rate(jvm.gc.pause{action="end of major GC"}[5m]) > 2
```
Alert when major GC occurs more than twice in 5 minutes.

### Repartitioning Data Loss
```
kafka.streams.repartition.records.dropped{application="LABS_GKE_DEPLOY"} > 0
```
Alert on any dropped records during repartitioning.

### RocksDB Compaction Backlog
```
rocksdb.num.pending.flushes{state-store=~".*"} > 5
```
Alert when flush operations are backing up.

---

## Alert Recommendations

### Critical Alerts (P1)

| Metric | Condition | Threshold | Action |
|--------|-----------|-----------|--------|
| `kafka.streams.thread.state` | Thread DEAD | == 0 | Restart application immediately |
| `kafka.streams.records.lag.max` | Critical lag | > 100000 | Scale up or investigate bottleneck |
| `jvm.memory.used` (heap) | Memory exhaustion | > 95% of max | Increase heap or investigate leak |
| `kafka.streams.repartition.records.dropped` | Data loss | > 0 | Investigate repartitioning logic |

### Warning Alerts (P2)

| Metric | Condition | Threshold | Action |
|--------|-----------|-----------|--------|
| `kafka.streams.records.lag` | High lag | > 10000 | Monitor and prepare to scale |
| `kafka.streams.process.latency.avg` | Slow processing | > 100ms | Investigate processing logic |
| `rocksdb.get.latency.p99` | Slow reads | > 10ms | Check disk I/O and cache |
| `jvm.gc.pause` (major) | Frequent GC | > 5/min | Tune GC or increase heap |

### Informational Alerts (P3)

| Metric | Condition | Threshold | Action |
|--------|-----------|-----------|--------|
| `rocksdb.num.running.compactions` | Ongoing compaction | > 0 | Monitor for extended duration |
| `kafka.streams.rebalance.latency.avg` | Slow rebalance | > 10s | Investigate cluster stability |
| `jvm.threads.live` | Thread growth | Increasing trend | Monitor for thread leak |

---

## Best Practices

### Metric Collection

1. **Sampling Rate**: RocksDB metrics are collected every 10 seconds by default
2. **Cardinality**: Be mindful of tag cardinality, especially with partition-level metrics
3. **Retention**: Configure appropriate retention in Dynatrace based on metric importance

### Dashboard Organization

1. **Overview Dashboard**: JVM health, Kafka Streams throughput, consumer lag
2. **Performance Dashboard**: Latency metrics (processing, commit, RocksDB operations)
3. **Resource Dashboard**: Memory usage, GC activity, thread states
4. **Troubleshooting Dashboard**: Error rates, dropped records, dead threads

### Monitoring Strategy

1. **Baseline**: Establish baseline metrics during normal operation
2. **Trends**: Monitor trends over time, not just absolute values
3. **Correlation**: Correlate metrics across layers (JVM → Kafka → RocksDB)
4. **Alerting**: Start with critical alerts, add warnings based on experience

---

## Troubleshooting Guide

### High Consumer Lag

1. Check `kafka.streams.records.consumed.rate` - is consumption rate dropping?
2. Check `kafka.streams.process.latency.avg` - is processing slow?
3. Check `rocksdb.get.latency.p99` - are state store reads slow?
4. Check `jvm.gc.pause` - is GC causing pauses?

### High Memory Usage

1. Check `jvm.memory.used` by pool - which pool is growing?
2. Check `rocksdb.memtable.size.all` - are MemTables accumulating?
3. Check `rocksdb.block.cache.usage` - is cache oversized?
4. Check `jvm.gc.memory.promoted` - is promotion rate high?

### Slow Processing

1. Check `kafka.streams.process.latency.avg` - baseline vs current
2. Check `rocksdb.get.latency.p99` - are reads slow?
3. Check `rocksdb.write.latency.p99` - are writes slow?
4. Check `system.cpu.usage` - is CPU saturated?

### Thread Issues

1. Check `kafka.streams.thread.state` - are threads healthy?
2. Check `jvm.threads.states{state="blocked"}` - thread contention?
3. Check `kafka.streams.rebalance.latency.avg` - frequent rebalancing?

---

## Additional Resources

- [Micrometer Documentation](https://micrometer.io/docs)
- [Dynatrace Metrics API](https://www.dynatrace.com/support/help/dynatrace-api/environment-api/metric-v2)
- [Kafka Streams Monitoring](https://kafka.apache.org/documentation/streams/developer-guide/monitoring)
- [RocksDB Statistics](https://github.com/facebook/rocksdb/wiki/Statistics)



kafka.streams.consumer.coordinator.metrics.commit.latency.avg
kafka.streams.consumer.coordinator.metrics.commit.latency.max
kafka.streams.consumer.fetch.manager.metrics.bytes.consumed.rate
kafka.streams.consumer.fetch.manager.metrics.records.consumed.rate
kafka.streams.consumer.fetch.manager.metrics.records.lag.max
kafka.streams.producer.metrics.record.send.rate
kafka.streams.stream.thread.metrics.poll.latency.avg
kafka.streams.stream.thread.metrics.process.rate

rocksdb_bytes_written
rocksdb_bytes_read
rocksdb_memtable_size_all
rocksdb_compact_write_bytes
rocksdb_db_get_micros