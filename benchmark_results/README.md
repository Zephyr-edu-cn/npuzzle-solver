# Benchmark Artifacts

- `Search_results_v2.csv`: current formal macro benchmark, using in-task nanosecond timing, five trials, five counterbalanced configurations, and a 120-second timeout.
- `Search_results.csv`: legacy corrected-LC run with millisecond timing started after task submission and fixed configuration order. Do not use its exact time values as current evidence.
- `Search_results_legacy_pairwise_lc.csv`: archived run from the old inadmissible pairwise Linear Conflict implementation.
- `jmh_state_transition_multifork.csv`: five-fork state-transition JMH run with GC profiling.

Methodology and summary statistics are documented in `docs/benchmark.md`.