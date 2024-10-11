package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  public static Lock lock = new ReentrantLock();
  private final Duration timeout;
  private final int maxDepth;
  private final PageParserFactory parserFactory;
  private final List<Pattern> ignoredUrls;

  @Inject
  ParallelWebCrawler(
          Clock clock,
          @Timeout Duration timeout,
          @PopularWordCount int popularWordCount,
          @TargetParallelism int threadCount,
          @IgnoredUrls List<Pattern> ignoredUrls,
          @MaxDepth int maxDepth,
          PageParserFactory parserFactory) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.maxDepth = maxDepth;
    this.parserFactory = parserFactory;
    this.ignoredUrls = ignoredUrls;
  }

  /* Received help from lead,
  Some code is re-used from SequentialWebCrawler
   */
  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);
    // Use of ConcurrentMap
    ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
    // Use of Concurrent Set
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();
    for (String url : startingUrls) {
      pool.invoke(new ForkCrawl(url, deadline, maxDepth, counts, visitedUrls, parserFactory));
    }
    pool.shutdown();
    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }
    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  /* Extension of RecursiveAction or RecursiveTask in custom class required,
   6 parameters,
   Received help from lead,
   Some code is re-used from SequentialWebCrawler*/
  public class ForkCrawl extends RecursiveAction {
    String url;
    Instant deadline;
    @MaxDepth int maxDepth;
    ConcurrentMap<String, Integer> counts;
    ConcurrentSkipListSet<String> visitedUrls;
    PageParserFactory parserFactory;

    public ForkCrawl(String url, Instant deadline, int maxDepth, ConcurrentMap<String, Integer> counts,
                     ConcurrentSkipListSet<String> visitedUrls, PageParserFactory parserFactory) {
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
      this.parserFactory = parserFactory;
    }

    @Override
    public void compute() {
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return;
      }
      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return;
        }
      }
      lock.lock();
      try {
        if (visitedUrls.contains(url)) {
          return;
        } else {
          visitedUrls.add(url);
        }
      } catch (NullPointerException ex) {
        ex.getLocalizedMessage();
      } finally {
        lock.unlock();
      }
      PageParser.Result result = parserFactory.get(url).parse();

      // Use of ConcurrentMap
      // Also based on SequentialWebCrawler
      for (ConcurrentMap.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
        if (counts.containsKey(e.getKey())) {
          counts.put(e.getKey(), e.getValue() + counts.get(e.getKey()));
        } else {
          counts.put(e.getKey(), e.getValue());
        }
      }
      List<ForkCrawl> subtasks = new ArrayList<>();
      for (String link : result.getLinks()) {
        subtasks.add(new ForkCrawl(link, deadline, maxDepth - 1, counts,
                visitedUrls, parserFactory));
      }
      invokeAll(subtasks);
    }
  }
  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
}