package com.eulerity.hackathon.imagefinder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.*;

public class WebScraper implements AutoCloseable{

    private Set<String> visitedUrls;
    private Set<String> scrapedImages;
    private ExecutorService executorService;
    private static final int DEFAULT_THREAD_POOL_SIZE = 5;
    private static final String USER_AGENT = "EulerityHackathonCrawler/1.0 (+https://drive.google.com/file/d/1Fze5FYIa3JvFH6VN4o39c5gR1A6tlNib/view)";
    private static final int DEFAULT_CRAWL_DELAY = 1000; // Default crawl delay in milliseconds


    public WebScraper() {
        this.visitedUrls = Collections.synchronizedSet(new HashSet<>());
        this.scrapedImages = Collections.synchronizedSet(new HashSet<>());
        this.executorService = Executors.newFixedThreadPool(5); // Adjust the number of threads as needed
    }

    /**
     * Initiates the web scraping process for a given starting URL.
     *
     * @param url The starting URL for web scraping.
     * @return A set of scraped image URLs.
     */
    public Set<String> scrapeWebsite(String url) {
        if(!executorService.isShutdown() && isUrlAllowed(url)) {
            scrapeUrl(url);
            System.out.println("Scraping website: " + url);
            try {
                // Wait for the executor to terminate
                executorService.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            close();
            // System.out.println("Scraping website: " + url);
        // Shut down the executor service after all threads are done
        }
        return Collections.unmodifiableSet(scrapedImages);
    }
    /**
     * Recursively scrapes URLs and images from a given URL using multiple threads.
     *
     * @param url The URL to scrape.
     */
    private void scrapeUrl(String url) {
        //System.out.println("Scraping url: " + url);
        if (!visitedUrls.contains(url) && !executorService.isShutdown()) {
            visitedUrls.add(url);

            // Use the ImageFinder servlet to make an HTTP request and get the HTML document
            executorService.execute(() -> {
                try {
                    //Thread.sleep((long)(Math.random() * 2000) + 1000); // Random delay between 1 to 3 seconds
                    int crawlDelay = getCrawlDelay(url);
                    if (crawlDelay > 0) {
                        Thread.sleep(crawlDelay);
                    }
                    // Set a descriptive User-Agent
                    Document document = Jsoup.connect(url).userAgent(USER_AGENT).get();

                    // Scraping Images
                    Elements images = document.select("img[src]");
                    for (Element image : images) {
                        String imageUrl = image.absUrl("src");
                        if(!imageUrl.isEmpty()) {
                            synchronized (scrapedImages) {
                                scrapedImages.add(imageUrl);
                                //System.out.println("Added image: " + imageUrl);
                            }
                        }
                    }

                    // Scraping URLs and crawling sub-pages
                    Elements links = document.select("a[href]");
                    for (Element link : links) {
                        String nextUrl = link.absUrl("href");

                        // Ensure the next URL is within the same domain
                        if (nextUrl.startsWith(url.substring(0, url.indexOf("/", 8)))) {
                            scrapeUrl(nextUrl);
                        }
                    }
                } catch (IOException | InterruptedException | RejectedExecutionException e) {
                    e.printStackTrace();
                    Logger.getLogger(WebScraper.class.getName()).log(Level.SEVERE, "Error during web scraping", e);
                }
            });
        }
    }

    /**
     * Get the set of scraped image URLs.
     *
     * @return Set of scraped image URLs.
     */
    public Set<String> getScrapedImages() {
        return Collections.unmodifiableSet(new HashSet<>(scrapedImages));
    }


    public void close() {
    try {
        executorService.shutdown();
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Executor service did not terminate");
            }
        }
    } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
    /**
     * Check if it's allowed to crawl the given URL based on robots.txt rules.
     *
     * @param url The URL to check.
     * @return True if allowed, false otherwise.
     */
    private boolean isUrlAllowed(String url) {
        String robotsTxtUrl = url + "/robots.txt";

        try {
            URLConnection connection = new URL(robotsTxtUrl).openConnection();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                String userAgent = "EulerityHackathonCrawler/1.0"; 
                boolean userAgentMatches = false;
    
                while ((line = reader.readLine()) != null) {
                    // Skip comments and empty lines
                    if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                        continue;
                    }
    
                    // Check for User-agent directive
                    if (line.toLowerCase().startsWith("user-agent")) {
                        String[] parts = line.split(":", 2);
                        String userAgentDirective = parts[1].trim().toLowerCase();
                        userAgentMatches = userAgent.equals(userAgentDirective) || userAgentDirective.equals("*");
                    }
    
                    // Check for Disallow directive
                    if (userAgentMatches && line.toLowerCase().startsWith("disallow")) {
                        String[] parts = line.split(":", 2);
                        String disallowDirective = parts[1].trim();
    
                        // Check if the URL matches the disallowed pattern
                        if (url.matches(".*" + disallowDirective.replace("*", ".*") + ".*")) {
                            return false; // Crawling is disallowed for this URL
                        }
                    }
                }
            }
        } catch (IOException e) {
            Logger.getLogger(WebScraper.class.getName()).log(Level.WARNING, "Robots.txt does not exist", e);
        }
    
        // Default to allowing crawling if no specific rule found
        return true;
    }

    private int getCrawlDelay(String url) {
        // Parse robots.txt for crawl delay
        try {
            String robotsUrl = url.substring(0, url.indexOf("/", 8)) + "/robots.txt";
            Document robotsTxt = Jsoup.connect(robotsUrl).ignoreContentType(true).get();
            String content = robotsTxt.text();

            // Example: "Crawl-delay: 5"
            String crawlDelayDirective = "Crawl-delay:";
            int index = content.indexOf(crawlDelayDirective);
            if (index != -1) {
                String value = content.substring(index + crawlDelayDirective.length()).trim();
                return Integer.parseInt(value) * 1000; // Convert to milliseconds
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return DEFAULT_CRAWL_DELAY;
    }
}

