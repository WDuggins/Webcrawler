package com.udacity.webcrawler.main;

import com.google.inject.Guice;
import com.udacity.webcrawler.WebCrawler;
import com.udacity.webcrawler.WebCrawlerModule;
import com.udacity.webcrawler.json.ConfigurationLoader;
import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.json.CrawlResultWriter;
import com.udacity.webcrawler.json.CrawlerConfiguration;
import com.udacity.webcrawler.profiler.Profiler;
import com.udacity.webcrawler.profiler.ProfilerModule;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class WebCrawlerMain {

  private final CrawlerConfiguration config;

  private WebCrawlerMain(CrawlerConfiguration config) {
    this.config = Objects.requireNonNull(config);
  }

  @Inject
  private WebCrawler crawler;

  @Inject
  private Profiler profiler;

  private void run() {
    Guice.createInjector(new WebCrawlerModule(config), new ProfilerModule()).injectMembers(this);

    CrawlResult result = crawler.crawl(config.getStartPages());
    CrawlResultWriter resultWriter = new CrawlResultWriter(result);

    /* Received help from lead */
    String profileOutput = config.getProfileOutputPath();
    Path path = Paths.get(profileOutput);
    // TODO: Write the crawl results to a JSON file (or System.out if the file name is empty)
    try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(System.out)) {
      if (config.getResultPath() == null || config.getResultPath().isEmpty()) {
        resultWriter.write(outputStreamWriter);
      } else {
        resultWriter.write(Path.of(config.getResultPath()));
      }
    // TODO: Write the profile data to a text file (or System.out if the file name is empty)
      if (profileOutput.isEmpty()) {
        profiler.writeData(outputStreamWriter);
      } else {
        profiler.writeData(path);
      }
    }catch(IOException e){
        e.printStackTrace();
      }
    }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("Usage: WebCrawlerMain [starting-url]");
      return;
    }

    CrawlerConfiguration config = new ConfigurationLoader(Path.of(args[0])).load();
    new WebCrawlerMain(config).run();
  }
}