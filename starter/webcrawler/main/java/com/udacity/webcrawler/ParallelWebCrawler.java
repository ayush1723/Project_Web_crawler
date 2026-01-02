package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;


/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final PageParserFactory parserFactory;
  private final int maxDepth;
  private final List<Pattern> ignoredUrls;
  private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
  private final Map<String, Integer> counts = new ConcurrentHashMap<>();

  @Inject
  ParallelWebCrawler(
      Clock clock,
      PageParserFactory parserFactory,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @MaxDepth int maxDepth,
      @IgnoredUrls List<Pattern> ignoredUrls,
      @TargetParallelism int threadCount) {
    this.clock = clock;
    this.parserFactory = parserFactory;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.maxDepth = maxDepth;
    this.ignoredUrls = ignoredUrls;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
      Instant deadline = clock.instant().plus(timeout);

      List<CrawlTask> tasks = startingUrls.stream()
              .map(url -> new CrawlTask(url, maxDepth, deadline))
              .collect(Collectors.toList());

      tasks.forEach(pool::invoke);


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

    private final class CrawlTask extends RecursiveAction {

        private final String url;
        private final int depth;
        private final Instant deadline;

        CrawlTask(String url, int depth, Instant deadline) {
            this.url = url;
            this.depth = depth;
            this.deadline = deadline;
        }

        @Override
        protected void compute() {
            if (depth == 0 || clock.instant().isAfter(deadline)) {
                return;
            }

            for (Pattern pattern : ignoredUrls) {
                if (pattern.matcher(url).matches()) {
                    return;
                }
            }

            if (!visitedUrls.add(url)) {
                return;
            }

            PageParser.Result result = parserFactory.get(url).parse();

            result.getWordCounts().forEach(
                    (word, count) -> counts.merge(word, count, Integer::sum)
            );

            List<CrawlTask> subtasks = result.getLinks().stream()
                    .map(link -> new CrawlTask(link, depth - 1, deadline))
                    .collect(Collectors.toList());

            invokeAll(subtasks);
        }
    }


    @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
}
