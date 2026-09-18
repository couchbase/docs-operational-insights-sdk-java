// tag::server-async[]
static void queryHandleExample(Queryable clusterOrScope) throws InterruptedException, TimeoutException {
    String slowStatement = """
      SELECT COUNT (1) AS c
          FROM
          ARRAY_RANGE(0,10000) AS d1,
          ARRAY_RANGE(0,10000) AS d2
      """;

    Duration timeout = Duration.ofMinutes(15);

    QueryHandle queryHandle = clusterOrScope.startQuery(
      slowStatement,
      opt -> opt.timeout(timeout)
    );

    QueryResultHandle resultHandle = waitForResult(queryHandle, timeout);
    try {
      // Process rows one by one as they arrive from the server.
      QueryMetadata metadata = resultHandle.streamRows(row -> System.out.println("Got row: " + row));
      System.out.println("Got metadata: " + metadata);

      // Alternatively, if the result is known to fit in memory:
      QueryResult buffered = resultHandle.bufferRows();
      System.out.println("Got result: " + buffered);

    } finally {
      // Tell the server it can forget the result.
      resultHandle.discard();
    }
  }

  private static QueryResultHandle waitForResult(
    QueryHandle queryHandle,
    Duration timeout
  ) throws InterruptedException, TimeoutException {
    final long timeoutNanos = timeout.toNanos();
    final long startNanos = System.nanoTime();

    while (true) {
      QueryStatus status = queryHandle.fetchStatus();
      if (status.resultReady()) return status.resultHandle();

      System.out.println("Waiting for query to finish; current status: " + status);

      long elapsedNanos = System.nanoTime() - startNanos;
      if (elapsedNanos > timeoutNanos) {
        throw new TimeoutException("Query result not ready after " + timeout);
      }

      SECONDS.sleep(1); // or use exponential backoff
    }
  }
// end::server-async[]

