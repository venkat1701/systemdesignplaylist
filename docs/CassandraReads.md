# System Design Case Study: Distributed Logging System with Cassandra

## Overview

Let's be honest; every modern application generates logs, and at scale, managing these logs becomes a fascinating distributed systems challenge. We're going to design a distributed logging system that can handle millions of log entries per second from thousands of microservices - think something like what powers systems at Netflix, Uber, or LinkedIn.

This case study will demonstrate not just how to design such a system, but more importantly, why NoSQL databases like Apache Cassandra are perfectly suited for this type of workload. Through building this system, we'll dive deep into Cassandra's internal architecture, data model, and distributed characteristics that make it shine for time-series, write-heavy workloads.

The system we're designing needs to ingest log data from distributed applications, store it efficiently, and provide fast querying capabilities for debugging, monitoring, and analytics purposes. What makes this particularly interesting is how Cassandra's unique architecture solves problems that traditional databases simply cannot handle at this scale.

## Requirements Gathering

### Functional Requirements

Our core requirements center around the fundamental operations that any logging system must support. Applications need to send log entries via HTTP API calls, which seems straightforward but becomes complex when we're talking about millions of entries per second. Users must be able to retrieve logs for specific services within time ranges, which is where Cassandra's time-series capabilities become crucial. The system should allow filtering by severity levels like INFO, WARN, and ERROR, requiring us to think carefully about our data model to support these query patterns efficiently.

Real-time streaming presents another challenge - users want to see logs as they arrive, not just query historical data. This means our system needs both batch processing for storage and stream processing for live updates. Additionally, users need to export historical logs for offline analysis, which requires efficient batch operations across potentially terabytes of data. Finally, retention management must automatically delete logs older than configured periods, and this needs to happen without impacting system performance.

We're intentionally keeping several features out of scope to focus on the core distributed systems challenges. User authentication and authorization will be handled by an API gateway, log parsing and structured extraction assumes logs arrive pre-structured, and we won't tackle advanced analytics, alerting systems, or multi-tenant isolation in this design.

### Non-Functional Requirements

The performance requirements drive every architectural decision we'll make. Handling over one million log entries per second with sub-100ms latency eliminates most traditional database solutions immediately. Query performance must return results within 2-5 seconds even when searching across massive datasets, which requires careful attention to data modeling and indexing strategies.

Availability requirements of 99.9% uptime with no single points of failure push us toward distributed architectures where every component can be replicated and failures can be handled gracefully. The system must scale linearly as data volume grows, meaning we can't rely on vertical scaling solutions that eventually hit hardware limits. Storage efficiency becomes critical when we're talking about compressing and storing over 100TB of log data cost-effectively.

This system represents enterprise-scale logging infrastructure that goes far beyond typical interview systems. We're dealing with throughput and storage requirements that only the largest technology companies face, which makes it an excellent vehicle for exploring advanced distributed systems concepts.

## Planning & Setup

### Strategic Approach

We'll build our design up sequentially, going through functional requirements one by one. This approach prevents us from getting lost in the weeds of any particular component and ensures we address every requirement systematically. Our functional requirements will drive the initial API and data flow design, while non-functional requirements will drive our deep dives into Cassandra's architecture and why it's perfect for this use case.

### Core Entities Definition

The beauty of our data model lies in its simplicity and alignment with Cassandra's strengths. Our primary entity is the LogEntry, representing a single log message with timestamp, service, level, message, and metadata. This entity is perfectly suited for Cassandra because it's immutable once written, naturally time-ordered, and we have massive write volume with relatively simple query patterns.

The Service entity represents microservices or applications generating logs, containing service name, version, and environment information. LogStream represents active streaming connections with connection metadata and filter information. What's elegant about this design is that LogEntry dominates our storage and query patterns, while the other entities primarily exist to support the user interface and connection management.

## API and System Interface Design

### Core API Design

Our log ingestion API supports both single entries and batch operations. The batch endpoint accepts arrays of LogEntry objects, which is crucial for high-throughput scenarios where applications can buffer logs locally before sending them in batches. The single entry endpoint handles real-time logging but includes important security considerations - the server overrides any client-provided timestamps to prevent timestamp manipulation attacks.

```typescript
POST /api/v1/logs/batch
{
  entries: LogEntry[]
}

POST /api/v1/logs/single
{
  timestamp: number,      // Server will override this
  service: string,
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL',
  message: string,
  metadata: Record<string, any>
}
```

Query APIs follow RESTful patterns with comprehensive filtering options. The primary logs endpoint accepts service name, time range, severity level, and pagination parameters. We return partial LogEntry objects to optimize network transfer, especially important when dealing with large result sets. The services endpoint provides metadata about available services, helping users construct effective queries.

```typescript
GET /api/v1/logs?service=user-service&start=1234567890&end=1234567999&level=ERROR&page=1&limit=100
-> Partial<LogEntry>[]

GET /api/v1/services -> Service[]
```

Real-time streaming uses WebSocket connections with query parameters that act as filters. This allows clients to subscribe to specific log streams without overwhelming their connections with irrelevant data. The streaming architecture needs to handle thousands of concurrent connections while maintaining low latency for log delivery.

```typescript
WebSocket /api/v1/logs/stream?service=user-service&level=ERROR
-> Stream<LogEntry>
```

Batch export operations follow an asynchronous pattern, returning export IDs immediately and allowing clients to poll for completion status. This design prevents long-running export operations from blocking API servers and provides a better user experience for large exports.

```typescript
POST /api/v1/logs/export
{
  service: string,
  startTime: number,
  endTime: number,
  format: 'json' | 'csv'
} -> { exportId: string }

GET /api/v1/logs/export/{exportId}/status
-> { status: 'pending' | 'ready' | 'failed', downloadUrl?: string }
```

### Security Considerations

Security in logging systems requires particular attention because logs often contain sensitive information and the high volume makes traditional security approaches impractical. We never trust client-provided data, especially timestamps, because they can be used to inject false historical data or create ordering issues. Service names come from API keys or JWT claims rather than request bodies, preventing services from impersonating each other.

Rate limiting per service prevents both accidental and malicious abuse, while server-generated UUIDs ensure we maintain control over data integrity. These security measures must be implemented efficiently since they apply to every one of our million-plus requests per second.

## High-Level Design

### Log Ingestion Architecture

The ingestion flow demonstrates how modern distributed systems handle massive scale through layered architectures. Client applications send log entries to load balancers, which distribute requests across multiple API servers. These API servers remain stateless, focusing solely on validation and enqueueing, which allows us to scale them horizontally based on incoming traffic.

Kafka serves as our message queue, providing reliable buffering between ingestion and storage. This decoupling is crucial because it allows the ingestion layer to handle traffic spikes without overwhelming the storage layer. Kafka topics are partitioned by service name, which provides natural load distribution and supports our streaming requirements.

Log processors consume from Kafka, batch entries for efficient Cassandra writes, and handle the complex task of writing to multiple denormalized tables. The Cassandra cluster stores all log data using a ring architecture that provides both horizontal scaling and fault tolerance.

### Cassandra Schema Design

Our schema showcases several key Cassandra concepts that make it perfect for this use case. The primary table uses a composite partition key combining service and log_date, which ensures even distribution across the cluster while keeping related data together. Clustering columns log_time and log_id provide natural time-based ordering and handle the edge case where multiple logs have identical timestamps.

```sql
CREATE TABLE logs_by_service_time (
    service text,
    log_date date,
    log_time timestamp,
    log_id timeuuid,
    level text,
    message text,
    metadata map<text, text>,
    PRIMARY KEY ((service, log_date), log_time, log_id)
) WITH CLUSTERING ORDER BY (log_time DESC, log_id DESC);

-- Secondary table for level-based queries
CREATE TABLE logs_by_service_level (
    service text,
    level text,
    log_date date,
    log_time timestamp,
    log_id timeuuid,
    message text,
    metadata map<text, text>,
    PRIMARY KEY ((service, level, log_date), log_time, log_id)
) WITH CLUSTERING ORDER BY (log_time DESC, log_id DESC);
```

We maintain a secondary table for level-based queries, demonstrating Cassandra's denormalization approach. Rather than using secondary indexes, which don't scale well in distributed systems, we duplicate data in multiple tables optimized for different query patterns. This trades storage space for query performance, which is typically the right trade-off for high-scale systems.

The schema design reflects deep understanding of how Cassandra stores and retrieves data. The partition key determines data distribution across nodes, while clustering columns determine sort order within partitions. This design supports our most common query patterns efficiently while avoiding the anti-patterns that cause performance problems in Cassandra deployments.

## Deep Dives into Non-Functional Requirements

### Write Performance and Cassandra's Architecture

Traditional RDBMS systems would crumble under our write load due to ACID transaction overhead and single-master bottlenecks. Cassandra's write path is fundamentally different and optimized for exactly this scenario. Every write first goes to a durable commit log, then to an in-memory structure called a MemTable, and the client receives acknowledgment immediately. Background processes handle flushing MemTables to disk as SSTables.

This architecture is fast for several reasons that distinguish it from traditional databases. Cassandra never performs read-before-write operations, unlike B-tree databases that must read pages before modifying them. All writes are sequential I/O operations, which maximizes disk throughput. The system uses lock-free data structures in MemTables, eliminating contention between concurrent writes. Finally, writes are automatically distributed across multiple nodes, preventing any single node from becoming a bottleneck.

Performance scales linearly with cluster size. A single node might handle 10,000-50,000 writes per second, while a 10-node cluster reaches 100,000-500,000 writes per second, and a 100-node cluster can exceed one million writes per second. This linear scaling is possible because Cassandra's architecture has no shared components that could become bottlenecks.

```
Single Node: ~10,000-50,000 writes/second
10-Node Cluster: ~100,000-500,000 writes/second  
100-Node Cluster: 1M+ writes/second (linear scaling)
```

Cassandra's tunable consistency model allows us to optimize for our specific requirements. Consistency Level ONE writes to one replica before acknowledging, providing maximum throughput with eventual consistency. Consistency Level QUORUM writes to majority of replicas before acknowledging, while Consistency Level ALL writes to all replicas with the highest latency. This is perfect for logging systems where slight delays in data propagation are acceptable in exchange for massive performance gains.

### Distributed Architecture and Fault Tolerance

Cassandra's ring architecture solves the fundamental challenge of distributed data storage - how to distribute data evenly across nodes while maintaining fault tolerance and avoiding single points of failure. Each node owns ranges of token values determined by consistent hashing, where partition keys are hashed to determine which nodes store the data.

The beauty of consistent hashing is that adding or removing nodes only affects immediate neighbors in the ring, making cluster operations predictable and efficient. Virtual nodes enhance this further by allowing each physical node to own multiple token ranges, which provides better load distribution and faster recovery operations when nodes fail or are added.

```
Node A: Token ranges 0-250, 750-1000
Node B: Token ranges 250-500  
Node C: Token ranges 500-750

Log entry with service="user-service", date="2024-01-01"
Hash(user-service:2024-01-01) = 350
→ Primary replica: Node B
→ Additional replicas: Node C, Node A (RF=3)
```

Replication strategies determine how data is copied across nodes. SimpleStrategy replicates to consecutive nodes in the ring, while NetworkTopologyStrategy replicates across racks and datacenters for better fault tolerance. For our logging system, we'd typically use NetworkTopologyStrategy with replication factor 3, ensuring data survives multiple simultaneous node failures.

Failure handling mechanisms ensure the system continues operating even when components fail. Hinted Handoff stores failed writes temporarily and replays them when nodes recover. Read Repair detects and fixes inconsistencies during read operations. Anti-Entropy Repair runs as a background process to ensure all replicas eventually converge, providing the "eventually consistent" guarantee that allows the system to remain available even during network partitions.

### Query Performance and Data Modeling

Achieving sub-5-second query performance across 100TB+ of data requires understanding Cassandra's read path and optimizing our data model accordingly. When a query arrives, Cassandra uses consistent hashing to identify the nodes that might contain the data. Bloom filters provide quick negative lookups, allowing nodes to quickly determine they don't contain requested data without expensive disk operations.

For nodes that might contain the data, partition indexes help locate the partition within SSTables, while clustering indexes navigate to specific clustering key ranges. Finally, data blocks are read and decompressed. Results from multiple SSTables are merged to provide the final result set.

Our partition key choice of service and log_date is carefully optimized for typical query patterns. Users typically query one service at a time, making service a natural partition boundary. Including log_date limits partition sizes to manageable levels - approximately 100MB for high-volume services, which stays within Cassandra's recommended partition size limits.

```
Average log entry: 1KB
High-volume service: 100,000 logs/day
Partition size: 100MB/day (within Cassandra's 100MB recommendation)
```

The clustering columns log_time and log_id provide natural time ordering, which aligns perfectly with how users want to view log data. Time range queries become efficient range scans within partitions. Multi-day queries require querying multiple partitions, but our query coordinator can parallelize these operations across multiple nodes.

```sql
-- Time Range Query (Efficient)
SELECT * FROM logs_by_service_time 
WHERE service = 'user-service' 
AND log_date = '2024-01-01'
AND log_time >= '2024-01-01 10:00:00'
AND log_time <= '2024-01-01 11:00:00';

-- Multi-Day Range (Requires Multiple Queries)
-- Query coordinator splits into multiple partition queries
service='user-service' AND log_date IN ('2024-01-01', '2024-01-02', '2024-01-03')

-- Level-Based Query (Uses Secondary Table)
SELECT * FROM logs_by_service_level
WHERE service = 'user-service' 
AND level = 'ERROR'
AND log_date = '2024-01-01';
```

Our indexing strategy avoids secondary indexes, which don't scale well in Cassandra's distributed architecture. Instead, we use denormalized tables, creating separate tables optimized for different query patterns. Application-level joins combine results when necessary, trading some complexity for predictable performance characteristics.

### Storage Efficiency and Lifecycle Management

Managing 100TB+ of data requires sophisticated storage and maintenance strategies. Cassandra's SSTable compaction strategies determine how data is organized on disk over time. Size-Tiered Compaction groups SSTables of similar size and works well for write-heavy workloads but can create very large SSTables over time. Leveled Compaction organizes SSTables in levels, providing better read performance but with more I/O overhead for writes.

Time-Window Compaction is perfect for time-series data like logs because it groups SSTables by time windows and enables efficient TTL-based deletion. For our logging system, we configure TWCS with hourly compaction windows, which aligns with our retention and query patterns.

```sql
CREATE TABLE logs_by_service_time (...) 
WITH compaction = {
  'class': 'TimeWindowCompactionStrategy',
  'compaction_window_unit': 'HOURS',
  'compaction_window_size': 1
};
```

Storage optimizations become critical at this scale. LZ4 compression typically reduces storage by 60-70% for log data, which contains many repeated strings and structured patterns. TTL-based deletion automatically expires old data without expensive delete operations, which is crucial for maintaining performance as data ages. Bloom filter tuning reduces false positive rates, minimizing unnecessary disk I/O during queries.

Retention management uses Cassandra's built-in TTL functionality, where data automatically expires after a specified time period. This approach is far more efficient than explicit delete operations, which create tombstones that can impact query performance until compaction removes them.

```sql
INSERT INTO logs_by_service_time (...) 
USING TTL 2592000;  -- 30 days in seconds
```

### Real-time Streaming Architecture

WebSocket connections need real-time updates as logs arrive while maintaining system scalability. We evaluated several architectural approaches for this requirement. Cassandra's Change Data Capture provides native integration but has limited CDC capabilities. Redis Pub/Sub offers low latency but has memory limitations that make it unsuitable for our scale.

| Approach | Pros | Cons |
|----------|------|------|
| **Cassandra Change Data Capture** | Native integration | Limited CDC capabilities |
| **Kafka Streaming** | High throughput, multiple consumers | Additional complexity |
| **Redis Pub/Sub** | Low latency | Memory limitations |

Our chosen solution uses Kafka-based streaming, which provides high throughput and supports multiple consumers with different filtering requirements. Log entries are published to Kafka immediately after API validation, allowing WebSocket servers to consume from Kafka with client-specific filters. This architecture scales because fan-out happens at the Kafka level rather than at the database level.

WebSocket servers maintain connections with clients and consume from Kafka topics. When messages arrive, servers filter them based on each client's subscription criteria and forward matching entries. Multiple WebSocket servers can consume from the same Kafka topics, distributing client connections across servers for better scalability.

```typescript
// WebSocket server pseudo-code
kafkaConsumer.subscribe(['logs-topic']);
kafkaConsumer.on('message', (message) => {
  const logEntry = JSON.parse(message.value);
  
  // Find matching WebSocket connections
  const matchingConnections = connections.filter(conn => 
    conn.filters.service === logEntry.service &&
    conn.filters.level.includes(logEntry.level)
  );
  
  // Send to matching clients
  matchingConnections.forEach(conn => 
    conn.send(JSON.stringify(logEntry))
  );
});
```

This design handles thousands of concurrent streaming connections while maintaining low latency for log delivery. Kafka's partitioning ensures that logs are distributed efficiently, while WebSocket servers can be added or removed based on connection load.

## Pattern Recognition and Technical Concepts

### Key Distributed Systems Patterns

Our design demonstrates several important patterns that appear frequently in large-scale distributed systems. The write-optimized data model leverages Cassandra's log-structured merge trees, which excel at write-heavy workloads because they never require read-before-write operations. This pattern appears in many time-series databases and is crucial for achieving high write throughput.

Denormalization for query performance represents a fundamental shift from traditional normalized database design. Rather than trying to minimize storage usage, we duplicate data across multiple tables optimized for different query patterns. This trades storage space for query speed and predictable performance, which is typically the right trade-off for large-scale systems.

Event-driven architecture appears in our use of Kafka for both reliable message delivery and real-time streaming. This pattern decouples system components, allowing them to scale independently and handle failures gracefully. The ingestion layer can handle traffic spikes without overwhelming the storage layer, while streaming consumers can process events at their own pace.

Time-series data management patterns are crucial for systems that deal with time-ordered data. Natural partitioning by time allows efficient range queries and supports TTL-based lifecycle management. These patterns appear in monitoring systems, IoT platforms, and financial trading systems.

### Cassandra-Specific Design Principles

Understanding the CAP theorem trade-offs is essential for working with Cassandra effectively. Cassandra is an AP system, prioritizing Availability and Partition-tolerance over strict Consistency. This means the system remains available even during network partitions, but data may be temporarily inconsistent across replicas. Tunable consistency allows per-query trade-offs, but for logging systems, choosing availability over consistency is typically correct.

Several anti-patterns can destroy performance in Cassandra deployments. Using Cassandra like an RDBMS with joins and complex relationships leads to poor performance because the system is optimized for different access patterns. Secondary indexes on high-cardinality columns don't scale well in distributed environments. Large partitions exceeding 100MB can cause hot spots and performance problems. Read-before-write patterns should be avoided through careful data modeling.

Operational considerations become crucial for production Cassandra deployments. The nodetool command-line utility handles cluster management operations like repairs, compaction, and node operations. Regular repair operations ensure data consistency across replicas, while compaction monitoring tracks SSTable counts and disk usage to prevent performance degradation.

## Final Design Integration

### Comprehensive System Architecture

Our complete system architecture demonstrates how all components work together to meet our ambitious requirements. Applications send logs through load balancers to API servers, which validate and enqueue entries to Kafka. WebSocket servers provide real-time streaming by consuming from the same Kafka topics that feed our storage pipeline.

```
[Applications] 
    ↓ (HTTP/gRPC)
[Load Balancer] 
    ↓
[API Servers] ← [WebSocket Servers]
    ↓                ↑
[Kafka Cluster] ----/
    ↓
[Log Processors]
    ↓
[Cassandra Cluster (Ring Architecture)]
    ↓
[Background Jobs: Compaction, Repair, Monitoring]
```

Log processors consume from Kafka, batch entries for efficient writes, and update multiple Cassandra tables to support different query patterns. The Cassandra cluster uses ring architecture for distribution and fault tolerance, while background jobs handle compaction, repair, and monitoring operations.

Several key architectural decisions shape this design. Choosing Cassandra as the primary store enables the write performance and horizontal scaling we need. Kafka provides reliable buffering and real-time streaming capabilities while decoupling system components. Denormalized data models optimize for query performance rather than storage efficiency. Time-Window Compaction aligns perfectly with time-series data lifecycle management. Accepting eventual consistency allows massive performance gains while maintaining system availability.

### Requirements Satisfaction

Our design meets each of our ambitious requirements through careful architectural choices. The requirement for over one million writes per second is satisfied by Cassandra's distributed write architecture combined with Kafka buffering. Sub-5-second query performance comes from optimized partition keys and clustering columns that align with query patterns.

The 99.9% availability requirement is met through elimination of single points of failure and automatic failover mechanisms throughout the system. Linear scaling is achieved by adding nodes to the Cassandra ring with automatic data redistribution. Storage of 100TB+ of data is handled through distribution across the cluster, compression, and TTL-based cleanup.

This design showcases why Cassandra excels for certain use cases while being honest about its limitations. It's not a universal solution, but for write-heavy, time-series workloads with relatively simple query patterns, it's often the best available choice.

## Level-Based Expectations

### Understanding Across Experience Levels

Mid-level engineers should focus on understanding why NoSQL solutions work better than SQL for this specific use case, grasping basic Cassandra concepts like partition keys and clustering columns, and understanding how distributed architectures provide horizontal scaling. The emphasis should be on breadth of understanding rather than deep implementation details.

Senior engineers need deeper Cassandra knowledge, including understanding of internal write and read paths, various compaction strategies, and their trade-offs. They should be able to design optimal partition keys, handle different query patterns, and understand operational requirements like monitoring, repair, and maintenance. Trade-off analysis becomes crucial, including explaining CAP theorem implications and consistency choices.

Staff-level engineers and above must demonstrate systems thinking by driving the entire design from requirements through operations. They need understanding of when to use Cassandra versus other NoSQL options, how to design for failure scenarios, capacity planning, and cost optimization. Architecture evolution planning becomes important, including how the system might grow and when technology migrations might be necessary.

The beauty of using Cassandra as a lens for system design is that it forces consideration of fundamental distributed systems concepts, data modeling trade-offs, and the characteristics that make certain databases suitable for specific workloads. These concepts transfer to many other distributed systems challenges beyond just database selection.