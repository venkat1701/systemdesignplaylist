# How GitHub Handles 5.5 Million Database Queries Per Second: A Scale Story


## The Numbers That Keep You Awake

Let me paint you a picture of GitHub's current database infrastructure: **1,200+ MySQL hosts processing 5.5 million queries per second across 50+ database clusters, managing 300+ terabytes of data.** When I first heard these numbers, I had to double-check them. That's not just "web scale"—that's "every developer on the planet using your platform simultaneously" scale.

But here's what really caught my attention: back in 2019, their main database cluster (affectionately called `mysql1`) was handling 950,000 queries per second. Fast forward to today, and they've nearly 6x'd that throughput while maintaining the reliability that 100+ million developers depend on daily.

The kicker? They did this without abandoning MySQL or falling into the "NoSQL will save us" trap. Instead, they doubled down on relational databases and built one of the most sophisticated MySQL architectures I've ever studied.

## The Monolith That Wouldn't Die

GitHub's story starts like every other successful startup: a single Ruby on Rails app with a single MySQL database. Simple, fast, and it worked. But by 2019, that innocuous `mysql1` cluster had become a beast:

- **950,000 queries per second** (900K on replicas, 50K on primary)
- **Every core GitHub feature** lived in this one database
- **Single point of failure** for the entire platform
- **Scaling vertically** by throwing bigger and bigger hardware at it

Here's the thing about database monoliths: they work incredibly well until they don't. GitHub was living on borrowed time, constantly upgrading to bigger EC2 instances and larger storage volumes. Every incident that touched `mysql1` affected users, repositories, issues, and pull requests simultaneously.

The obvious solution? "Let's just shard the database!" But anyone who's tried to retrofit sharding onto a mature application knows the truth: **sharding is easy in theory, nightmarish in practice.**

## The Schema Domain Revolution

GitHub's engineering team came up with something I hadn't seen before: **schema domains**. Instead of randomly splitting tables or trying to guess future access patterns, they analyzed their actual application code to understand which tables belonged together.

A schema domain is essentially a cohesive group of tables that:
- Are frequently joined together in queries
- Participate in the same transactions
- Change together for the same business reasons
- Have natural foreign key relationships

For example, the "gists" schema domain contains:
- `gists` table (the core data)
- `gist_comments` table (related comments)
- `starred_gists` table (user interactions)

These tables naturally cluster together because they're always used together. You can't have gist comments without gists, and starring involves both users and gists.

**The genius move**: They built SQL linters that prevented cross-domain queries during development. This created virtual partitions in their codebase before they attempted physical partitions in the database.

## The Technical Stack That Actually Worked

GitHub's current architecture is a masterclass in pragmatic engineering:

### MySQL 8.0 as the Foundation
They recently upgraded their entire fleet to MySQL 8.0 (a year-long project in itself). MySQL remains their core storage engine because:
- **Operational expertise**: 15+ years of MySQL knowledge doesn't disappear overnight
- **Ecosystem maturity**: Percona Toolkit, gh-ost, orchestrator, freno—the tooling is battle-tested
- **Transactional consistency**: ACID properties matter when you're managing code repositories

### ProxySQL for Connection Management
With hundreds of Rails application instances connecting to databases, they hit MySQL's 16,000 connection limit quickly. ProxySQL acts as an intelligent proxy that:
- **Connection pooling**: Multiplexes thousands of app connections over a smaller pool of database connections
- **Query routing**: Automatically routes reads to replicas and writes to primaries
- **Failover handling**: Seamlessly redirects traffic during maintenance or failures

The Ruby on Rails ecosystem wasn't designed for this scale, so ProxySQL bridges that gap elegantly.

### Vitess for Horizontal Sharding
For their largest schema domains, GitHub uses Vitess—YouTube's open-source MySQL sharding solution. Vitess provides:
- **VTGate**: A stateless proxy that handles query routing across shards
- **Horizontal sharding**: Splitting large tables across multiple MySQL instances
- **Online schema migrations**: Changing table structure without downtime
- **Shard management**: Moving data between shards as they grow

But here's the pragmatic part: they don't use Vitess everywhere. Only for the domains that actually need horizontal sharding.

### Custom Write-Cutover Process
GitHub built their own migration tool as a backup to Vitess. Using MySQL's native replication, they can move entire schema domains between clusters with minimal downtime:

1. Set up the destination cluster as a replica of the source
2. Configure ProxySQL to route traffic to the original cluster
3. Execute a rapid cutover script that:
    - Enables read-only mode on the source
    - Waits for replication to catch up
    - Breaks replication
    - Updates ProxySQL routing to the new cluster

The entire cutover takes seconds, but preparation can take weeks.

## The Main Challenges

### Challenge 1: The Connection Explosion

Ruby on Rails creates database connections liberally. With hundreds of application instances and dozens of database clusters, GitHub was hitting connection limits constantly. Each Rails process wanted connections to every database it might need.

**The Solution**: ProxySQL connection multiplexing. Instead of 1,000 Rails processes each holding 50 database connections (50,000 total), ProxySQL maintains a smaller pool and reuses connections efficiently. This dropped their connection usage by 90%+ while actually improving performance.

### Challenge 2: Cross-Shard Queries

Once you partition data, some queries inevitably need data from multiple partitions. Traditional approaches require expensive scatter-gather operations or give up on consistency.

**GitHub's Approach**: They use their schema domain boundaries to minimize cross-partition queries at the application level. Their SQL linters prevent developers from writing queries that would require cross-shard operations. When they absolutely need cross-domain data, they handle it in application code with careful consistency guarantees.

### Challenge 3: Operational Complexity

Managing 1,200+ database hosts isn't just about the happy path—it's about upgrades, failures, monitoring, and debugging across a fleet.

**Their Tools**:
- **gh-ost**: For online schema migrations without locking
- **Orchestrator**: For automated failover and topology management
- **freno**: For throttling operations based on replication lag
- **Custom automation**: Deployment pipelines, monitoring, and alerting

The upgrade to MySQL 8.0 alone took over a year and required coordination across multiple teams.

### Challenge 4: Read-Your-Writes Consistency

With aggressive read replica usage, users might write data on the primary but immediately read from a lagging replica, seeing stale data.

**The Solution**: Strategic use of primary reads for critical paths and careful cache invalidation. They can't eliminate the problem, but they minimize user-visible inconsistencies through application logic.

## The Performance Results

The numbers speak for themselves:

**Before partitioning (2019)**:
- 950,000 queries/second on mysql1
- Frequent scaling bottlenecks
- All-or-nothing failure scenarios

**After partitioning (2024)**:
- 5.5 million queries/second across 50+ clusters
- Isolated failure domains
- Horizontal scaling capability
- Sub-second response times maintained

But the real win isn't just throughput—it's **operational stability**. When one schema domain has issues, it doesn't bring down the entire platform.

## The Hidden Complexity

What GitHub doesn't talk about as much is the hidden complexity this architecture introduces:

### Distributed Transactions
Some operations still need to touch multiple schema domains. GitHub handles this primarily through careful application design and eventual consistency patterns, not distributed transactions.

### Data Analytics
Running reports across 50+ database clusters is non-trivial. They likely use replica data stores and ETL processes to create unified views for analytics.

### Development Environment Parity
How do you give developers a realistic local environment when production spans 1,200+ database hosts? This is an ongoing challenge for any company at this scale.

### Cost Management
Running 1,200+ database hosts isn't cheap. The operational overhead includes not just hardware costs but also the engineering team size needed to manage this infrastructure.

## Looking Forward

GitHub's architecture today represents years of evolutionary improvements rather than revolutionary changes. They've proven that:

- **Boring technology** (MySQL) can scale to incredible heights with the right architecture
- **Incremental improvements** often beat big rewrites
- **Operational expertise** is more valuable than cutting-edge technology
- **Application-level boundaries** are crucial for database scalability

As they continue growing, I'd expect to see:
- More aggressive use of Vitess for horizontal sharding
- Continued investment in automation and tooling
- Possible adoption of newer MySQL features like multi-primary setups
- Integration with cloud-native database services where appropriate

## The Bottom Line

GitHub handles 5.5 million database queries per second not through revolutionary technology, but through disciplined engineering, pragmatic technology choices, and relentless focus on operational excellence.

They didn't abandon MySQL for the latest NoSQL database. They didn't rewrite their entire application. Instead, they carefully analyzed their access patterns, built clean boundaries in their code, and invested in the tooling needed to operate at scale.

The most impressive part? Their users rarely notice the complexity. GitHub just works, even as millions of developers push code, open issues, and collaborate on projects simultaneously.

That's the hallmark of truly great infrastructure engineering: **making the impossible look effortless.**